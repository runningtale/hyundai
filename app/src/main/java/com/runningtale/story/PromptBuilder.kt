package com.runningtale.story

import com.runningtale.model.AgeBand
import com.runningtale.model.InterludeTrigger
import com.runningtale.model.PlaceKind
import com.runningtale.model.StorySkeleton
import com.runningtale.model.TimeOfDay
import com.runningtale.model.TripState

/**
 * LLM 프롬프트 생성.
 * 아이 이름, 주소, 좌표는 넣지 않음. 주인공은 항상 `{주인공}`으로 표기하게 함.
 *
 * TODO(담당 1): 문구 다듬기, 연령대별 예시 문장 추가
 */
class PromptBuilder {

    val systemPrompt: String = """
        너는 차 뒷좌석의 어린이에게 들려줄 오디오 동화를 쓰는 작가다.
        규칙:
        - 주인공은 반드시 {주인공} 이라고만 쓴다. 다른 이름을 붙이지 않는다.
        - 죽음, 폭력, 다침, 길을 잃음, 무서운 괴물, 부모와 헤어짐은 쓰지 않는다.
        - 문장 하나는 40자 이내로 짧게 쓴다.
        - 소리로만 듣는 이야기라 그림을 설명하지 말고 소리, 바람, 빛, 느낌으로 쓴다.
        - effect.type은 reading_light(빛이 켜지는 장면) 또는 fan(바람이 부는 장면)만 쓴다. 장 하나에 효과는 최대 2개.
        - 반드시 요청한 JSON 형식만 출력한다. 설명 문장을 붙이지 않는다.
    """.trimIndent()

    fun skeletonPrompt(age: AgeBand, trip: TripState): String = """
        새 이야기의 뼈대와 1장을 만들어라.
        ${tripLines(trip)}
        - 듣는 아이 연령대: ${age.label}
        - 결말 장소: ${endingHint(trip)}
        - 장 수: ${chapterCount(trip)}개 안팎

        출력 JSON:
        {"title": "", "world": "", "characters": [{"name": "{주인공}", "voice": "haeun", "role": "주인공"}],
         "ending_place": "", "chapter_plan": [""], "first_chapter": $CHAPTER_SCHEMA}
        voice는 seoyoon, eunwoo, haeun, sanghyeok 중 하나. 해설은 haeun.
    """.trimIndent()

    fun chapterPrompt(
        skeleton: StorySkeleton,
        storySoFar: String,
        trip: TripState,
        index: Int,
        isFinal: Boolean,
    ): String = """
        이야기 "${skeleton.title}"의 ${index}장을 써라.
        세계: ${skeleton.world}
        장 계획: ${skeleton.chapterPlan.joinToString(" / ")}
        지금까지 줄거리: $storySoFar
        ${tripLines(trip)}
        ${if (isFinal) "- 이번 장이 마지막이다. ${skeleton.endingPlace}에 도착하며 끝낸다." else "- 아직 결말을 내지 않는다."}

        출력 JSON: $CHAPTER_SCHEMA
    """.trimIndent()

    fun interludePrompt(skeleton: StorySkeleton, storySoFar: String, trigger: InterludeTrigger, trip: TripState): String = """
        이야기 "${skeleton.title}" 중간에 끼워 넣을 1~3문장을 써라.
        지금까지 줄거리: $storySoFar
        방금 실제 도로에서 생긴 일: ${triggerHint(trigger)}
        ${tripLines(trip)}
        이 일을 이야기 속 장면으로 바꿔서 자연스럽게 이어지게 한다.

        출력 JSON: {"trigger": "${trigger.jsonName}", "lines": [{"speaker": "narrator", "text": ""}]}
    """.trimIndent()

    private fun tripLines(trip: TripState): String = buildString {
        trip.remainSec?.let { append("- 도착까지 약 ${it / 60}분\n") }
        append("- 시간대: ${timeHint(trip.timeOfDay)}\n")
        append("- 비: ${if (trip.isRaining) "오는 중" else "안 옴"}")
    }

    private fun endingHint(trip: TripState): String =
        if (trip.placeKind == PlaceKind.BLOCKED || trip.destinationName == null) {
            "집으로 돌아오는 따뜻한 길"
        } else {
            "${trip.destinationName} (장소 종류: ${trip.placeKind.name})를 이야기 속 장소로 바꾼 곳"
        }

    private fun chapterCount(trip: TripState): Int {
        val minutes = (trip.remainSec ?: 1800) / 60
        return (minutes / 12).coerceIn(2, 12)
    }

    private fun timeHint(t: TimeOfDay) = when (t) {
        TimeOfDay.MORNING -> "아침"
        TimeOfDay.DAY -> "낮"
        TimeOfDay.EVENING -> "해 질 녘"
        TimeOfDay.NIGHT -> "밤"
    }

    private fun triggerHint(t: InterludeTrigger) = when (t) {
        InterludeTrigger.TUNNEL -> "터널에 들어감"
        InterludeTrigger.TOLL -> "요금소를 지남"
        InterludeTrigger.REST_AREA -> "휴게소 근처를 지남"
        InterludeTrigger.RAIN_START -> "비가 오기 시작함"
        InterludeTrigger.RAIN_STOP -> "비가 그침"
        InterludeTrigger.NIGHT_FALL -> "날이 어두워짐"
    }

    private companion object {
        const val CHAPTER_SCHEMA = """{"index": 1, "title": "", "lines": [{"speaker": "narrator", "text": "", "effect": {"type": "fan", "level": 1, "duration_sec": 40}}], "summary": "", "is_final": false}"""
    }
}
