package com.runningtale.story

/**
 * 이름 뒤 조사를 받침에 맞춤 (기기 안에서만 동작). 템플릿에 어느 쪽 조사가 써 있어도 이름에 맞게 바꿈.
 * 조사 뒤에 한글이 이어지면(`{주인공}이에요`) 조사가 아닐 수 있어 건드리지 않음 → 이야기 파일 규칙 docs/03 참고
 */
object Josa {

    private val PAIRS = listOf("은" to "는", "이" to "가", "을" to "를", "과" to "와", "으로" to "로", "아" to "야")

    private const val PARTICLES = "(으로|은|는|이|가|을|를|과|와|로|아|야)(?![가-힣])"

    /** 앱의 자리표시는 `{주인공}` 하나라 미리 만들어 둠 */
    private val DEFAULT = Regex(Regex.escape("{주인공}") + PARTICLES)

    /** [placeholder]를 [name]으로 바꾸고 바로 뒤 조사를 받침에 맞춤 */
    fun fill(text: String, placeholder: String, name: String): String {
        val particle = if (placeholder == "{주인공}") DEFAULT else Regex(Regex.escape(placeholder) + PARTICLES)
        return text
            .replace(particle) { name + pick(name, it.groupValues[1]) }
            .replace(placeholder, name)
    }

    /** [name] 뒤에 올 조사. [given]은 짝 중 아무 쪽이나 */
    fun pick(name: String, given: String): String {
        val pair = PAIRS.firstOrNull { given == it.first || given == it.second } ?: return given
        val last = name.lastOrNull() ?: return given
        if (last !in '가'..'힣') return given
        val jong = (last - '가') % 28
        val withBatchim = if (pair.first == "으로") jong != 0 && jong != RIEUL else jong != 0
        return if (withBatchim) pair.first else pair.second
    }

    private const val RIEUL = 8
}
