package com.runningtale.story

import com.runningtale.model.Chapter
import com.runningtale.model.ChildProfile
import com.runningtale.model.Interlude
import com.runningtale.model.InterludeTrigger
import com.runningtale.model.StorySkeleton
import com.runningtale.model.TripState

/**
 * API 키가 없을 때(제출용 APK) 쓰는 생성기. 미리 만든 이야기를 순서대로 돌려줌.
 *
 * TODO(담당 4): assets/stories/의 데모 이야기 3편을 읽어 목적지 종류에 맞는 것을 고르기
 */
class DemoStoryGenerator(
    private val safety: SafetyFilter,
) : StoryGenerator {

    private lateinit var childName: String

    override suspend fun createSkeleton(child: ChildProfile, trip: TripState): StorySkeleton {
        childName = child.name
        return safety.personalize(FallbackLines.defaultSkeleton, childName)
    }

    override suspend fun nextChapter(
        skeleton: StorySkeleton,
        storySoFar: String,
        trip: TripState,
        index: Int,
        isFinal: Boolean,
    ): Chapter {
        val chapters = FallbackLines.defaultChapters
        val chapter = if (isFinal) chapters.last() else chapters.getOrElse(index - 2) { chapters.last() }
        return safety.personalize(chapter.copy(index = index), childName)
    }

    override suspend fun interlude(
        skeleton: StorySkeleton,
        storySoFar: String,
        trigger: InterludeTrigger,
        trip: TripState,
    ): Interlude = safety.personalize(Interlude(trigger, FallbackLines.interlude(trigger)), childName)
}
