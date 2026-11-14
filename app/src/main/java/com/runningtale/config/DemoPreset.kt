package com.runningtale.config

import com.runningtale.model.AgeBand
import com.runningtale.model.ChildProfile
import com.runningtale.model.SeatPosition

/**
 * 설정 화면에서 아이를 등록하기 전에 쓰는 기본값 (ChildStore). 나이, 카시트는 화면에 없어 항상 이 값.
 * 실제 사람 이름을 쓰지 않음.
 */
object DemoPreset {
    val child = ChildProfile(
        name = "하윤",
        ageBand = AgeBand.AGE_6_7,
        seat = SeatPosition.REAR_LEFT,
        usesCarSeat = true,
    )
}
