package com.runningtale.story

import com.runningtale.config.AppConfig
import com.runningtale.model.Chapter
import com.runningtale.model.Effect
import com.runningtale.model.EffectType
import com.runningtale.model.Interlude
import com.runningtale.model.InterludeTrigger
import com.runningtale.model.PlaceKind
import com.runningtale.model.StoryCharacter
import com.runningtale.model.StoryLine
import com.runningtale.model.StorySkeleton
import com.runningtale.model.Voice
import org.json.JSONArray
import org.json.JSONObject

/**
 * LLM 응답 JSON → model. 형식은 docs/03_모듈간_약속.md 3장.
 * 허용되지 않은 효과는 버리고, 효과 시간은 상한으로 자름. 형식이 틀리면 예외 (호출 쪽에서 대체).
 */
class StoryParser {

    fun parseSkeleton(json: String): StorySkeleton = skeletonFrom(JSONObject(stripCodeFence(json)))

    /**
     * 데모 이야기 파일(assets/stories 폴더의 JSON) → DemoStory. 형식은 docs/03_모듈간_약속.md 3장 "데모 이야기 파일".
     * 모르는 장소 종류, 모르는 끼어들기 키는 무시함
     */
    fun parseDemoStory(json: String): DemoStory {
        val o = JSONObject(json)
        val interludes = o.optJSONObject("interludes")
        return DemoStory(
            id = o.optString("id"),
            places = o.optJSONArray("places").mapStrings()
                .mapNotNull { name -> PlaceKind.entries.firstOrNull { it.name == name } }
                .toSet(),
            skeleton = skeletonFrom(o.getJSONObject("skeleton")),
            chapters = o.getJSONArray("chapters").mapObjects(::chapterFrom),
            interludes = InterludeTrigger.entries
                .mapNotNull { t -> interludes?.optJSONArray(t.jsonName)?.let { t to linesFrom(it).take(3) } }
                .toMap(),
        )
    }

    private fun skeletonFrom(o: JSONObject): StorySkeleton {
        return StorySkeleton(
            title = o.getString("title"),
            world = o.optString("world"),
            characters = o.optJSONArray("characters").mapObjects {
                StoryCharacter(it.getString("name"), Voice.fromId(it.optString("voice")), it.optString("role"))
            },
            endingPlace = o.optString("ending_place"),
            chapterPlan = o.optJSONArray("chapter_plan").mapStrings(),
            firstChapter = chapterFrom(o.getJSONObject("first_chapter")),
        )
    }

    fun parseChapter(json: String): Chapter = chapterFrom(JSONObject(stripCodeFence(json)))

    fun parseInterlude(json: String, trigger: InterludeTrigger): Interlude {
        val o = JSONObject(stripCodeFence(json))
        return Interlude(trigger, linesFrom(o.getJSONArray("lines")).take(3))
    }

    private fun chapterFrom(o: JSONObject) = Chapter(
        index = o.optInt("index", 1),
        title = o.optString("title"),
        lines = linesFrom(o.getJSONArray("lines")),
        summary = o.optString("summary"),
        isFinal = o.optBoolean("is_final", false),
    )

    private fun linesFrom(arr: JSONArray): List<StoryLine> = arr.mapObjects { l ->
        StoryLine(
            speaker = l.optString("speaker", "narrator"),
            text = l.getString("text"),
            effect = l.optJSONObject("effect")?.let(::effectFrom),
        )
    }.filter { it.text.isNotBlank() }

    private fun effectFrom(e: JSONObject): Effect? {
        val type = EffectType.fromJson(e.optString("type")) ?: return null
        val max = when (type) {
            EffectType.READING_LIGHT -> AppConfig.READING_LIGHT_MAX_SEC
            EffectType.FAN -> AppConfig.FAN_MAX_SEC
        }
        return Effect(type, level = 1, durationSec = e.optInt("duration_sec", max).coerceIn(5, max))
    }

    /** 모델이 ```json ... ``` 으로 감싸서 줄 때 대비 */
    private fun stripCodeFence(s: String): String =
        s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

    private fun JSONArray?.mapStrings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
