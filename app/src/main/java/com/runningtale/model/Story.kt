package com.runningtale.model

/** Pleos TTS 한국어 화자. */
enum class Voice(val pleosId: String) {
    SEOYOON("seoyoon"),
    EUNWOO("eunwoo"),
    HAEUN("haeun"),
    SANGHYEOK("sanghyeok"),
    ;

    companion object {
        val NARRATOR = HAEUN
        fun fromId(id: String?): Voice = entries.firstOrNull { it.pleosId == id } ?: NARRATOR
    }
}

/** 1차 효과 2종. */
enum class EffectType(val jsonName: String) {
    READING_LIGHT("reading_light"),
    FAN("fan"),
    ;

    companion object {
        fun fromJson(name: String?): EffectType? = entries.firstOrNull { it.jsonName == name }
    }
}

/** 문장에 붙는 효과. 실제 실행 여부는 EffectController 규칙이 결정. */
data class Effect(
    val type: EffectType,
    val level: Int = 1,
    val durationSec: Int,
)

/** 낭독 한 줄. speaker는 "narrator" 또는 등장인물 이름. */
data class StoryLine(
    val speaker: String,
    val text: String,
    val effect: Effect? = null,
)

data class Chapter(
    val index: Int,
    val title: String,
    val lines: List<StoryLine>,
    /** 다음 생성 요청에 넘길 줄거리 요약 */
    val summary: String,
    val isFinal: Boolean,
)

data class StoryCharacter(
    val name: String,
    val voice: Voice,
    val role: String,
)

/** 출발할 때 만드는 이야기 뼈대(1층). */
data class StorySkeleton(
    val title: String,
    val world: String,
    val characters: List<StoryCharacter>,
    val endingPlace: String,
    val chapterPlan: List<String>,
    val firstChapter: Chapter,
) {
    fun voiceOf(speaker: String): Voice =
        characters.firstOrNull { it.name == speaker }?.voice ?: Voice.NARRATOR
}

enum class InterludeTrigger(val jsonName: String) {
    TUNNEL("tunnel"),
    TOLL("toll"),
    REST_AREA("rest_area"),
    RAIN_START("rain_start"),
    RAIN_STOP("rain_stop"),
    NIGHT_FALL("night_fall"),
}

/** 도로 이벤트에 끼워 넣는 1~3문장(3층). */
data class Interlude(
    val trigger: InterludeTrigger,
    val lines: List<StoryLine>,
)
