package com.runningtale.config

import com.runningtale.model.AgeBand
import com.runningtale.model.ChildProfile
import com.runningtale.model.SeatPosition

/**
 * 1차 시연용 아이 정보. 화면이 없으므로 여기서 정함 (2차에 음성 등록).
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
