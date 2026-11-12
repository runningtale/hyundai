package com.runningtale.story

/**
 * 제공사 구현이 들어오기 전 자리 채움. 항상 실패 → LlmStoryGenerator가 기본 이야기로 대체.
 * TODO(담당 1): 제공사 구현으로 교체하고 이 파일 삭제
 */
class NotConfiguredLlmClient : LlmClient {
    override suspend fun complete(systemPrompt: String, userPrompt: String): String =
        throw UnsupportedOperationException("LLM 제공사 구현이 아직 없음")
}
