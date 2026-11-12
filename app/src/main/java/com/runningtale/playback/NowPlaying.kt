package com.runningtale.playback

/**
 * 차 미디어 화면의 재생 카드에 보일 내용. StoryDirector가 갱신하고 StoryMediaService가 메타데이터로 넘김.
 * 화면은 Pleos 미디어 앱이 그리며, 우리는 글자와 그림 정보만 넘김 (docs/07 4-1절).
 */
data class NowPlaying(
    /** 이야기 제목. 아이 이름으로 치환된 상태 (화면 표시용, LLM에 다시 보내지 않음) */
    val storyTitle: String? = null,
    val chapterIndex: Int? = null,
    val chapterTitle: String? = null,
    /** 결말까지 다 읽음 */
    val finished: Boolean = false,
)
