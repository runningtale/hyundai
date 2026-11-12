package com.runningtale.story

import android.content.res.AssetManager
import android.util.Log
import com.runningtale.model.Chapter
import com.runningtale.model.ChildProfile
import com.runningtale.model.Interlude
import com.runningtale.model.InterludeTrigger
import com.runningtale.model.PlaceKind
import com.runningtale.model.StoryLine
import com.runningtale.model.StorySkeleton
import com.runningtale.model.TripState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 미리 써 둔 이야기 한 편. 파일 형식은 docs/03_모듈간_약속.md 3장 "데모 이야기 파일". */
data class DemoStory(
    val id: String,
    /** 이 이야기를 고를 목적지 종류. 비어 있으면 맞는 이야기가 없을 때만 쓰임 */
    val places: Set<PlaceKind>,
    val skeleton: StorySkeleton,
    /** 2장부터 결말까지 순서대로. 마지막 장은 is_final = true */
    val chapters: List<Chapter>,
    val interludes: Map<InterludeTrigger, List<StoryLine>>,
)

/**
 * API 키가 없을 때(제출용 APK) 쓰는 생성기. assets/stories 폴더의 이야기 중 목적지 종류에 맞는 한 편을 순서대로 돌려줌.
 *
 * 고르는 순서: places에 지금 목적지 종류가 있는 첫 파일 → 없으면 파일 이름순 첫 파일 → 파일이 하나도 없으면 FallbackLines
 */
class DemoStoryGenerator(
    private val assets: AssetManager,
    private val parser: StoryParser,
    private val safety: SafetyFilter,
) : StoryGenerator {

    private lateinit var childName: String
    private var stories: List<DemoStory>? = null
    private var story: DemoStory? = null

    override suspend fun createSkeleton(child: ChildProfile, trip: TripState): StorySkeleton {
        childName = child.name
        val all = stories ?: withContext(Dispatchers.IO) { load() }.also { stories = it }
        story = all.firstOrNull { trip.placeKind in it.places } ?: all.firstOrNull()
        Log.i(TAG, "데모 이야기 ${story?.id ?: "기본(FallbackLines)"}, 목적지 종류 ${trip.placeKind}")
        return safety.personalize(story?.skeleton ?: FallbackLines.defaultSkeleton, childName)
    }

    override suspend fun nextChapter(
        skeleton: StorySkeleton,
        storySoFar: String,
        trip: TripState,
        index: Int,
        isFinal: Boolean,
    ): Chapter {
        val chapters = story?.chapters?.takeIf { it.isNotEmpty() } ?: FallbackLines.defaultChapters
        val chapter = if (isFinal) chapters.last() else chapters.getOrElse(index - 2) { chapters.last() }
        // 결말 여부는 파일 값이 아니라 위치로 정함. 파일에 is_final이 빠지거나 잘못 들어가도
        // 결말 반복(도착 후 같은 장을 계속 요청)이나 이른 종료가 생기지 않게
        val final = isFinal || chapter === chapters.last()
        return safety.personalize(chapter.copy(index = index, isFinal = final), childName)
    }

    override suspend fun interlude(
        skeleton: StorySkeleton,
        storySoFar: String,
        trigger: InterludeTrigger,
        trip: TripState,
    ): Interlude {
        val lines = story?.interludes?.get(trigger) ?: FallbackLines.interlude(trigger)
        return safety.personalize(Interlude(trigger, lines), childName)
    }

    /** 형식이 틀린 파일은 로그만 남기고 건너뜀 */
    private fun load(): List<DemoStory> =
        (assets.list(DIR) ?: emptyArray())
            .filter { it.endsWith(".json") }
            .sorted()
            .mapNotNull { name ->
                runCatching { parser.parseDemoStory(assets.open("$DIR/$name").bufferedReader().use { it.readText() }) }
                    .onFailure { Log.w(TAG, "데모 이야기 $name 읽기 실패", it) }
                    .getOrNull()
            }
            .also { Log.i(TAG, "데모 이야기 ${it.size}편: ${it.map(DemoStory::id)}") }

    private companion object {
        const val TAG = "DemoStoryGenerator"
        const val DIR = "stories"
    }
}
