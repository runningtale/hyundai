package com.runningtale.story

import com.runningtale.model.Chapter
import com.runningtale.model.Effect
import com.runningtale.model.EffectType
import com.runningtale.model.InterludeTrigger
import com.runningtale.model.StoryCharacter
import com.runningtale.model.StoryLine
import com.runningtale.model.StorySkeleton
import com.runningtale.model.Voice

/**
 * 생성이 늦거나 실패했을 때 쓰는 문장과 기본 이야기.
 * `{주인공}`은 SafetyFilter.personalize에서 치환.
 *
 * TODO(담당 4): 기본 이야기를 assets/stories 폴더의 JSON 파일로 옮기고 3편으로 늘리기
 */
object FallbackLines {

    /** 출발 직후 생성 대기 시간을 가리는 도입부 */
    val opening = listOf(
        StoryLine("narrator", "오늘의 모험이 시작돼요."),
        StoryLine("narrator", "{주인공}, 준비됐나요? 귀를 쫑긋 세워 봐요."),
    )

    /** 다음 장이 늦을 때 시간을 버는 이음 문장 */
    val bridge = listOf(
        StoryLine("narrator", "잠깐, 저 멀리서 무언가 반짝여요."),
        StoryLine("narrator", "{주인공}은 숨을 크게 쉬고 다시 길을 나섰어요."),
    )

    /** 예고 없는 이벤트에 LLM이 늦을 때 */
    fun interlude(trigger: InterludeTrigger): List<StoryLine> = listOf(
        when (trigger) {
            InterludeTrigger.TUNNEL -> StoryLine("narrator", "와, 반짝이는 동굴에 들어왔어요!")
            InterludeTrigger.TOLL -> StoryLine("narrator", "커다란 성문이 활짝 열렸어요.")
            InterludeTrigger.REST_AREA -> StoryLine("narrator", "쉬어 가는 마을이 보여요.")
            InterludeTrigger.RAIN_START -> StoryLine(
                "narrator", "어? 하늘에서 빗방울이 톡톡!",
                Effect(EffectType.FAN, durationSec = 20),
            )
            InterludeTrigger.RAIN_STOP -> StoryLine("narrator", "비가 그치고 햇살이 살짝 비쳐요.")
            InterludeTrigger.NIGHT_FALL -> StoryLine("narrator", "하늘에 별이 하나둘 떠올라요.")
        },
    )

    /** 키가 없거나 첫 생성이 실패했을 때의 기본 이야기 (짧은 3장) */
    val defaultSkeleton = StorySkeleton(
        title = "{주인공}의 바람 마을 여행",
        world = "바람이 노래하는 하늘 길",
        characters = listOf(
            StoryCharacter("{주인공}", Voice.HAEUN, "주인공"),
            StoryCharacter("통통이", Voice.EUNWOO, "길잡이 구름"),
        ),
        endingPlace = "따뜻한 쉼터 마을",
        chapterPlan = listOf("구름 마을 출발", "바람 행성", "쉼터 마을 도착"),
        firstChapter = Chapter(
            index = 1,
            title = "구름 마을 출발",
            lines = listOf(
                StoryLine("narrator", "구름 마을에 아침이 밝았어요."),
                StoryLine("통통이", "{주인공}, 오늘은 멀리 가 볼까?"),
                StoryLine("narrator", "{주인공}은 통통이 위에 폴짝 올라탔어요."),
            ),
            summary = "주인공과 길잡이 구름 통통이가 구름 마을을 떠났다.",
            isFinal = false,
        ),
    )

    val defaultChapters = listOf(
        Chapter(
            index = 2,
            title = "바람 행성",
            lines = listOf(
                StoryLine("narrator", "휘이잉, 바람 행성에 도착했어요."),
                StoryLine("통통이", "꽉 잡아! 바람이 분다!", Effect(EffectType.FAN, durationSec = 40)),
                StoryLine("narrator", "{주인공}의 머리카락이 살랑살랑 흔들려요."),
            ),
            summary = "바람 행성을 지났다.",
            isFinal = false,
        ),
        Chapter(
            index = 3,
            title = "쉼터 마을 도착",
            lines = listOf(
                StoryLine("narrator", "저기 따뜻한 불빛이 보여요.", Effect(EffectType.READING_LIGHT, durationSec = 20)),
                StoryLine("통통이", "다 왔어, {주인공}! 정말 멋진 여행이었어."),
                StoryLine("narrator", "오늘의 모험은 여기까지예요."),
            ),
            summary = "쉼터 마을에 도착했다.",
            isFinal = true,
        ),
    )
}
