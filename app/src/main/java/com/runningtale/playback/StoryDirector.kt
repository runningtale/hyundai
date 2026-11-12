package com.runningtale.playback

import android.util.Log
import com.runningtale.config.AppConfig
import com.runningtale.model.Chapter
import com.runningtale.model.ChildProfile
import com.runningtale.model.DriveEvent
import com.runningtale.model.Effect
import com.runningtale.model.EffectType
import com.runningtale.model.Gear
import com.runningtale.model.Interlude
import com.runningtale.model.InterludeTrigger
import com.runningtale.model.PlaceKind
import com.runningtale.model.StoryLine
import com.runningtale.model.StorySkeleton
import com.runningtale.model.TimeOfDay
import com.runningtale.model.TripState
import com.runningtale.model.Voice
import com.runningtale.story.FallbackLines
import com.runningtale.story.SafetyFilter
import com.runningtale.story.StoryGenerator
import com.runningtale.vehicle.EffectController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/**
 * 판단의 중심 (담당 3). docs/02_구조설계.md 3장.
 *
 * - 1층: 재생 시작 시 도입부를 읽는 동안 뼈대 + 1장 생성
 * - 2층: 지금 장의 남은 낭독 시간이 NEXT_CHAPTER_LEAD_SEC 이하이면 다음 장 요청
 * - 3층: 예고 이벤트는 미리 생성해 두었다가 진입 시 끼워 넣고, 예고 없는 이벤트는 즉시 요청
 *
 * 모든 상태 변경은 메인 스코프 하나에서만 일어남 (동시 수정 방지).
 *
 * TODO(담당 3): 일시정지 후 재개 위치, 다음 장 건너뛰기, 장 사이 조용한 구간, 효과음
 */
class StoryDirector(
    private val scope: CoroutineScope,
    private val generator: StoryGenerator,
    private val narrator: Narrator,
    private val effects: EffectController,
    private val safety: SafetyFilter,
    private val child: ChildProfile,
    events: Flow<DriveEvent>,
) {
    var trip = TripState()
        private set

    private val _nowPlaying = MutableStateFlow(NowPlaying())

    /** 재생 카드 내용. 장의 첫 문장을 읽기 시작할 때 바뀜 */
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying

    private val _destinationLabel = MutableStateFlow<String?>(null)

    /** 목록 부제에 보일 목적지 이름. 분류가 BLOCKED면 null (병원, 장례식장 등은 화면에도 쓰지 않음) */
    val destinationLabel: StateFlow<String?> = _destinationLabel

    private var skeleton: StorySkeleton? = null
    private val storySoFar = StringBuilder()
    private var chapterIndex = 1
    private var finalQueued = false
    private var arrived = false

    /** 본 이야기 줄. 장의 첫 줄에는 그 장을 달아 두어 읽기 시작할 때 재생 카드를 바꿈 */
    private data class Queued(val line: StoryLine, val chapterStart: Chapter? = null)

    private val mainQueue = ArrayDeque<Queued>()
    private val interludeQueue = ArrayDeque<StoryLine>()
    private val prepared = mutableMapOf<InterludeTrigger, Deferred<Interlude>>()
    private val lastEnteredAt = mutableMapOf<InterludeTrigger, Long>()

    private var playJob: Job? = null
    private var nextChapterJob: Job? = null
    private var storyStarted = false

    /** 장 하나를 기다리는 동안 이음 문장은 한 번만. 새 장이 들어오면 다시 false */
    private var bridged = false

    val isPlaying: Boolean get() = playJob?.isActive == true

    init {
        scope.launch { events.collect { onEvent(it) } }
    }

    fun play() {
        if (isPlaying) return
        if (arrived && finalQueued) reset() // 도착 뒤 다시 재생하면 새 이야기
        if (!storyStarted) {
            storyStarted = true
            startStory()
        }
        playJob = scope.launch { playLoop() }
    }

    private fun reset() {
        nextChapterJob?.cancel()
        prepared.values.forEach { it.cancel() }
        prepared.clear()
        mainQueue.clear()
        interludeQueue.clear()
        storySoFar.clear()
        skeleton = null
        chapterIndex = 1
        finalQueued = false
        arrived = false
        storyStarted = false
        bridged = false
        _nowPlaying.value = NowPlaying()
    }

    fun pause() {
        playJob?.cancel()
        narrator.stop()
        effects.restoreAll()
    }

    private fun startStory() {
        mainQueue.addAll(FallbackLines.opening.map { Queued(it.withName()) })
        scope.launch {
            val s = generator.createSkeleton(child, trip)
            skeleton = s
            appendChapter(s.firstChapter)
        }
    }

    private suspend fun playLoop() {
        while (scope.isActive) {
            val queued = interludeQueue.removeFirstOrNull()?.let { Queued(it) } ?: mainQueue.removeFirstOrNull()
            if (queued == null) {
                if (arrived && finalQueued) {
                    _nowPlaying.value = _nowPlaying.value.copy(finished = true)
                    break
                }
                waitForMore()
                continue
            }
            queued.chapterStart?.let(::showChapter)
            val line = queued.line
            line.effect?.let { effects.apply(it, child.seat, trip) }
            narrator.speak(line.text, skeleton?.voiceOf(line.speaker) ?: Voice.NARRATOR)
            maybeRequestNextChapter()
        }
        effects.restoreAll()
    }

    private fun showChapter(chapter: Chapter) {
        _nowPlaying.value = NowPlaying(
            storyTitle = skeleton?.title?.replace(SafetyFilter.PLACEHOLDER, child.name),
            chapterIndex = chapter.index,
            chapterTitle = chapter.title,
        )
    }

    /** 다음 장이 아직 안 왔으면 이음 문장으로 한 번 시간을 벌고 기다림. 결말 뒤에는 도착까지 조용히 */
    private suspend fun waitForMore() {
        if (!bridged && skeleton != null && !finalQueued) {
            bridged = true
            mainQueue.addAll(FallbackLines.bridge.map { Queued(it.withName()) })
        } else {
            delay(300)
        }
    }

    // ---- 2층: 다음 장 ----

    private fun maybeRequestNextChapter() {
        val s = skeleton ?: return
        if (finalQueued || nextChapterJob?.isActive == true) return
        if (estimatedRemainSec(mainQueue) > AppConfig.NEXT_CHAPTER_LEAD_SEC) return

        val nextIndex = chapterIndex + 1
        val isFinal = (trip.remainSec ?: Int.MAX_VALUE) <= AppConfig.FINAL_CHAPTER_REMAIN_SEC || arrived
        nextChapterJob = scope.launch {
            val chapter = generator.nextChapter(s, storySoFar.toString(), trip, nextIndex, isFinal)
            appendChapter(chapter)
        }
    }

    private fun appendChapter(chapter: Chapter) {
        chapterIndex = chapter.index
        bridged = false
        mainQueue.addAll(chapter.lines.mapIndexed { i, line -> Queued(line, chapterStart = chapter.takeIf { i == 0 }) })
        storySoFar.append(chapter.summary).append(' ')
        if (chapter.isFinal) finalQueued = true
    }

    /** 한국어 TTS 대략 초당 5자 + 문장 사이 0.5초 [가정: 측정 후 조정] */
    private fun estimatedRemainSec(lines: Collection<Queued>): Int =
        lines.sumOf { it.line.text.length / 5 + 1 }

    // ---- 이벤트와 3층 ----

    private fun onEvent(event: DriveEvent) {
        Log.d(TAG, "event $event")
        when (event) {
            is DriveEvent.GearChanged -> trip = trip.copy(isMoving = event.gear == Gear.DRIVE)
            is DriveEvent.DestinationUpdated -> {
                trip = trip.copy(
                    destinationName = event.name,
                    placeKind = safety.classifyPlace(event.name),
                    remainSec = event.remainSec,
                )
                _destinationLabel.value = event.name.takeIf { trip.placeKind != PlaceKind.BLOCKED }
            }
            is DriveEvent.RainChanged -> {
                val changed = trip.isRaining != event.raining
                trip = trip.copy(isRaining = event.raining)
                if (changed && isPlaying) {
                    surprise(if (event.raining) InterludeTrigger.RAIN_START else InterludeTrigger.RAIN_STOP)
                }
            }
            // TODO(담당 3): 해 질 녘에 전조등이 켜지면 NIGHT_FALL 끼어들기 (터널 진입과 구별 필요)
            is DriveEvent.HeadlightChanged -> trip = trip.copy(isHeadlightOn = event.on, timeOfDay = timeOfDay(event.on))
            is DriveEvent.RoadEventAhead -> roadEvent(event)
            is DriveEvent.Arrived -> {
                arrived = true
                if (!finalQueued) maybeRequestNextChapter()
            }
            // TODO(담당 3): 1차는 아이 탑승을 DemoPreset으로 가정. 2차에 착석·뒷문으로 판단
            is DriveEvent.RearSeatChanged, is DriveEvent.RearDoorChanged -> Unit
        }
    }

    /** 예고 이벤트: 멀리서는 미리 생성, 가까워지면 재생 */
    private fun roadEvent(e: DriveEvent.RoadEventAhead) {
        val s = skeleton ?: return
        val now = System.currentTimeMillis()
        if (now - (lastEnteredAt[e.trigger] ?: 0L) < SAME_EVENT_GAP_MS) return

        if (e.distanceM > AppConfig.ROAD_EVENT_ENTER_M) {
            if (prepared[e.trigger] == null) {
                prepared[e.trigger] = scope.async { generator.interlude(s, storySoFar.toString(), e.trigger, trip) }
            }
            return
        }
        lastEnteredAt[e.trigger] = now
        val job = prepared.remove(e.trigger)
        scope.launch {
            val interlude = job?.let { withTimeoutOrNull(500) { it.await() } }
                ?: Interlude(e.trigger, FallbackLines.interlude(e.trigger).map { it.withName() })
            interludeQueue.addAll(interlude.lines)
        }
    }

    /**
     * 예고 없는 이벤트: 즉시 요청, 늦으면 범용 문장 (generator 안에서 처리)
     * 이벤트에 붙는 효과는 생성 응답을 기다리지 않고 바로 실행 (docs/02 3-4절, docs/07 3-2절).
     * 끼어들기 문장에 같은 효과가 또 붙어 와도 EffectController의 실행 중·쿨다운 규칙에 걸려 두 번 실행되지 않음
     */
    private fun surprise(trigger: InterludeTrigger) {
        IMMEDIATE_EFFECTS[trigger]?.let { effects.apply(it, child.seat, trip) }
        val s = skeleton ?: return
        scope.launch {
            val interlude = generator.interlude(s, storySoFar.toString(), trigger, trip)
            interludeQueue.addAll(interlude.lines)
        }
    }

    private fun timeOfDay(headlightOn: Boolean): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 6 || hour >= 20 -> TimeOfDay.NIGHT
            hour >= 17 -> if (headlightOn) TimeOfDay.NIGHT else TimeOfDay.EVENING
            hour < 10 -> TimeOfDay.MORNING
            else -> TimeOfDay.DAY
        }
    }

    private fun StoryLine.withName(): StoryLine =
        copy(text = text.replace(SafetyFilter.PLACEHOLDER, child.name))

    private companion object {
        const val TAG = "StoryDirector"
        const val SAME_EVENT_GAP_MS = 60_000L

        val IMMEDIATE_EFFECTS = mapOf(
            InterludeTrigger.RAIN_START to Effect(EffectType.FAN, durationSec = AppConfig.RAIN_FAN_SEC),
        )
    }
}
