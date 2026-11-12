package com.runningtale.model

/** 아이 연령대. LLM에는 이 값만 보냄 (실제 나이, 이름은 보내지 않음). */
enum class AgeBand(val label: String) {
    AGE_4_5("4~5세"),
    AGE_6_7("6~7세"),
    AGE_8_9("8~9세"),
}

/** 뒷좌석 자리. Pleos SeatValue.ROW_2_* 에 대응. */
enum class SeatPosition {
    REAR_LEFT,
    REAR_CENTER,
    REAR_RIGHT,
}

/**
 * 부모가 등록한 아이 정보. 기기 안에만 저장.
 * @param name 이야기 속 호칭. 외부로 보내지 않고 `{주인공}` 자리표시를 기기 안에서 치환할 때만 씀
 */
data class ChildProfile(
    val name: String,
    val ageBand: AgeBand,
    val seat: SeatPosition,
    val usesCarSeat: Boolean,
)
