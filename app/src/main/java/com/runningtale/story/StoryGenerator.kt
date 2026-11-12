package com.runningtale.story

import com.runningtale.model.Chapter
import com.runningtale.model.ChildProfile
import com.runningtale.model.Interlude
import com.runningtale.model.InterludeTrigger
import com.runningtale.model.StorySkeleton
import com.runningtale.model.TripState

/**
 * 이야기 생성 (담당 1). docs/03_모듈간_약속.md 3장.
 *
 * - 실패해도 예외를 던지지 않고 FallbackLines로 채워서 반환
 * - 반환 문장의 `{주인공}`은 이미 아이 이름으로 바뀐 상태
 */
interface StoryGenerator {
    /** 1층: 출발할 때 뼈대 + 1장 */
    suspend fun createSkeleton(child: ChildProfile, trip: TripState): StorySkeleton

    /** 2층: 지금 장이 끝나기 전에 다음 한 장 */
    suspend fun nextChapter(
        skeleton: StorySkeleton,
        storySoFar: String,
        trip: TripState,
        index: Int,
        isFinal: Boolean,
    ): Chapter

    /** 3층: 도로 이벤트 끼어들기 1~3문장 */
    suspend fun interlude(
        skeleton: StorySkeleton,
        storySoFar: String,
        trigger: InterludeTrigger,
        trip: TripState,
    ): Interlude
}
