package com.runningtale.story

/**
 * 외부 LLM 호출. 제공사(Gemini, Claude 등)는 이 인터페이스 구현만 바꿔 끼움.
 *
 * TODO(담당 1): 제공사 결정 후 구현 클래스 추가 (예: GeminiLlmClient)
 *  - HTTPS만 사용 (Pleos 보안 가이드: TLS 1.2 이상)
 *  - 키는 AppConfig.llmApiKey. 코드에 직접 쓰지 않음
 *  - 응답은 JSON 문자열만 반환하도록 요청 (JSON 출력 모드가 있으면 사용)
 *  - 타임아웃을 짧게 (끼어들기는 AppConfig.INTERLUDE_TIMEOUT_MS 안에 와야 의미 있음)
 */
interface LlmClient {
    /** @return 모델이 출력한 JSON 문자열. 실패하면 예외 */
    suspend fun complete(systemPrompt: String, userPrompt: String): String
}
