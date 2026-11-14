package com.runningtale.story

import com.runningtale.model.Chapter
import com.runningtale.model.Interlude
import com.runningtale.model.PlaceKind
import com.runningtale.model.StoryLine
import com.runningtale.model.StorySkeleton

/**
 * 아이 안전 검사와 개인정보 처리 (기기 안에서만 동작).
 *
 * TODO(담당 1): 키워드 목록 보강
 */
class SafetyFilter {

    /**
     * 목적지 이름으로 장소 종류 분류.
     * - 금지 장소(장례식장, 병원 등)는 BLOCKED, 어느 키워드에도 안 맞으면 OTHER
     * - 목적지 이름은 분류된 장소(BLOCKED, OTHER 제외)일 때만 LLM에 보냄 (PromptBuilder.endingHint)
     */
    fun classifyPlace(name: String?): PlaceKind {
        if (name.isNullOrBlank()) return PlaceKind.OTHER
        if (BLOCKED_PLACE_WORDS.any { name.contains(it) }) return PlaceKind.BLOCKED
        return PLACE_KEYWORDS.entries.firstOrNull { (_, words) -> words.any { name.contains(it) } }?.key
            ?: PlaceKind.OTHER
    }

    /** 생성 문장에 금칙어가 있으면 false */
    fun isSafe(lines: List<StoryLine>): Boolean =
        lines.none { line -> BANNED_WORDS.any { line.text.contains(it) } }

    fun isSafe(skeleton: StorySkeleton): Boolean =
        isSafe(skeleton.firstChapter.lines) && BANNED_WORDS.none { skeleton.title.contains(it) }

    /** `{주인공}`을 아이 이름으로 치환. 이름은 이 함수 밖으로 나가지 않음 */
    fun personalize(chapter: Chapter, childName: String): Chapter =
        chapter.copy(lines = chapter.lines.map { it.personalize(childName) })

    fun personalize(interlude: Interlude, childName: String): Interlude =
        interlude.copy(lines = interlude.lines.map { it.personalize(childName) })

    /** 제목은 다음 생성 요청에 다시 쓰이므로 치환하지 않음 (낭독하지 않음) */
    fun personalize(skeleton: StorySkeleton, childName: String): StorySkeleton =
        skeleton.copy(
            characters = skeleton.characters.map { it.copy(name = it.name.replace(PLACEHOLDER, childName)) },
            firstChapter = personalize(skeleton.firstChapter, childName),
        )

    private fun StoryLine.personalize(name: String) = copy(
        speaker = speaker.replace(PLACEHOLDER, name),
        text = Josa.fill(text, PLACEHOLDER, name),
    )

    companion object {
        const val PLACEHOLDER = "{주인공}"

        private val BLOCKED_PLACE_WORDS = listOf(
            "장례", "추모", "납골", "화장장", "병원", "의원", "응급", "요양", "경찰서", "교도소", "법원",
        )

        private val PLACE_KEYWORDS: Map<PlaceKind, List<String>> = linkedMapOf(
            PlaceKind.SEA to listOf("해변", "해수욕장", "바다", "해안", "항구"),
            PlaceKind.MOUNTAIN to listOf("산", "계곡", "휴양림", "고개"),
            PlaceKind.AMUSEMENT to listOf("랜드", "월드", "동물원", "아쿠아리움", "키즈", "놀이"),
            PlaceKind.PARK to listOf("공원", "수목원", "식물원"),
            PlaceKind.FAMILY_HOME to listOf("할머니", "할아버지", "외가", "본가", "친가"),
            PlaceKind.HOME to listOf("우리집", "집"),
            PlaceKind.LODGING to listOf("호텔", "리조트", "펜션", "캠핑", "글램핑"),
            PlaceKind.RESTAURANT to listOf("식당", "맛집", "카페", "레스토랑"),
            PlaceKind.SCHOOL to listOf("어린이집", "유치원", "학교", "학원"),
        )

        private val BANNED_WORDS = listOf(
            "죽었", "죽어", "죽음", "피가", "칼", "총", "귀신", "유령", "괴물", "때리", "다쳤", "길을 잃", "버려",
        )
    }
}
