package com.runningtale.config

import com.runningtale.BuildConfig

object AppConfig {
    /** local.properties의 LLM_API_KEY. release 빌드는 항상 빈 값. */
    val llmApiKey: String = BuildConfig.LLM_API_KEY

    /** 키가 없으면 미리 만든 이야기로 동작 (제출용 APK) */
    val isDemoMode: Boolean get() = llmApiKey.isBlank()

    /** 지금 장이 이만큼 남으면 다음 장을 요청 (2층) */
    const val NEXT_CHAPTER_LEAD_SEC = 90

    /** 남은 도착 시간이 이 이하면 결말 장을 요청 */
    const val FINAL_CHAPTER_REMAIN_SEC = 180

    /** 터널 등 예고 이벤트를 미리 생성하기 시작하는 거리 (3층) */
    const val ROAD_EVENT_PREPARE_M = 1000

    /** 이 거리 이하가 되면 진입으로 보고 끼어들기 재생 */
    const val ROAD_EVENT_ENTER_M = 50

    /** 예고 없는 이벤트의 끼어들기를 기다리는 최대 시간. 넘으면 범용 문장 */
    const val INTERLUDE_TIMEOUT_MS = 5_000L

    /** 다음 장 생성이 이 시간 넘게 실패하면 기본 이야기로 대체 */
    const val CHAPTER_TIMEOUT_MS = 30_000L

    /** 효과 상한 (docs/03_모듈간_약속.md 4장) */
    const val READING_LIGHT_MAX_SEC = 30
    const val FAN_MAX_SEC = 60
}
