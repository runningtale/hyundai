package com.runningtale.story

import com.runningtale.config.AppConfig
import com.runningtale.model.Chapter
import com.runningtale.model.ChildProfile
import com.runningtale.model.Interlude
import com.runningtale.model.InterludeTrigger
import com.runningtale.model.StorySkeleton
import com.runningtale.model.TripState
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 프롬프트 → LLM → 파싱 → 안전 검사 → 이름 치환.
 * 실패하면 1회 재시도 후 FallbackLines로 대체 (예외를 밖으로 던지지 않음).
 */
class LlmStoryGenerator(
    private val llm: LlmClient,
    private val prompts: PromptBuilder,
    private val parser: StoryParser,
    private val safety: SafetyFilter,
) : StoryGenerator {

    private lateinit var childName: String

    override suspend fun createSkeleton(child: ChildProfile, trip: TripState): StorySkeleton {
        childName = child.name
        val skeleton = retryOnce(AppConfig.CHAPTER_TIMEOUT_MS) {
            parser.parseSkeleton(llm.complete(prompts.systemPrompt, prompts.skeletonPrompt(child.ageBand, trip)))
                .takeIf { safety.isSafe(it) }
        } ?: FallbackLines.defaultSkeleton
        return safety.personalize(skeleton, childName)
    }

    override suspend fun nextChapter(
        skeleton: StorySkeleton,
        storySoFar: String,
        trip: TripState,
        index: Int,
        isFinal: Boolean,
    ): Chapter {
        val chapter = retryOnce(AppConfig.CHAPTER_TIMEOUT_MS) {
            parser.parseChapter(
                llm.complete(prompts.systemPrompt, prompts.chapterPrompt(skeleton, storySoFar, trip, index, isFinal)),
            ).takeIf { safety.isSafe(it.lines) }
        } ?: fallbackChapter(index, isFinal)
        return safety.personalize(chapter.copy(index = index, isFinal = isFinal), childName)
    }

    override suspend fun interlude(
        skeleton: StorySkeleton,
        storySoFar: String,
        trigger: InterludeTrigger,
        trip: TripState,
    ): Interlude {
        val interlude = withTimeoutOrNull(AppConfig.INTERLUDE_TIMEOUT_MS) {
            attempt {
                parser.parseInterlude(
                    llm.complete(prompts.systemPrompt, prompts.interludePrompt(skeleton, storySoFar, trigger, trip)),
                    trigger,
                ).takeIf { safety.isSafe(it.lines) }
            }
        } ?: Interlude(trigger, FallbackLines.interlude(trigger))
        return safety.personalize(interlude, childName)
    }

    private fun fallbackChapter(index: Int, isFinal: Boolean): Chapter {
        val chapters = FallbackLines.defaultChapters
        return if (isFinal) chapters.last() else chapters.first()
    }

    /** 제한 시간 안에서 최대 2번 시도. 형식 오류, 안전 검사 실패(null), 네트워크 오류 모두 재시도 대상 */
    private suspend fun <T> retryOnce(timeoutMs: Long, block: suspend () -> T?): T? =
        withTimeoutOrNull(timeoutMs) {
            repeat(2) {
                val result = attempt { block() }
                if (result != null) return@withTimeoutOrNull result
            }
            null
        }

    /** 실패하면 null. 단 코루틴 취소는 삼키지 않고 그대로 전달 (runCatching은 취소까지 삼킴) */
    private suspend fun <T> attempt(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "생성 실패, 대체 사용", e)
            null
        }

    private companion object {
        const val TAG = "LlmStoryGenerator"
    }
}
