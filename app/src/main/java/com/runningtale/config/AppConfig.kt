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

    /**
     * 이 거리 이하가 되면 진입으로 보고 끼어들기 재생.
     * 내비 정보를 5초마다 받으므로 시속 100km(5초에 약 140m)에서도 놓치지 않도록 넉넉히 잡음.
     * 지금 문장을 끝까지 읽은 뒤 재생되므로 실제 진입 시점과 비슷해짐 [가정: 실차에서 조정]
     */
    const val ROAD_EVENT_ENTER_M = 300

    /** 예고 없는 이벤트의 끼어들기를 기다리는 최대 시간. 넘으면 범용 문장 */
    const val INTERLUDE_TIMEOUT_MS = 5_000L

    /** 다음 장 생성이 이 시간 넘게 실패하면 기본 이야기로 대체 */
    const val CHAPTER_TIMEOUT_MS = 30_000L

    /** 효과 상한 (docs/03_모듈간_약속.md 4장) */
    const val READING_LIGHT_MAX_SEC = 30
    const val FAN_MAX_SEC = 60

    /**
     * 같은 종류 효과 사이 최소 간격 [가정: 3분, docs/07 3-2절]. 자주 바뀌면 운전자와 아이 모두 산만함.
     * 효과가 2종이라 이 간격이면 한 장(1~2분)에 효과는 많아야 2개 → "장당 최대 2개" 규칙도 같이 지켜짐
     */
    const val EFFECT_COOLDOWN_SEC = 180

    /** 비가 오기 시작하면 LLM 응답을 기다리지 않고 바로 켜는 풍량 효과 시간 */
    const val RAIN_FAN_SEC = 20
}
