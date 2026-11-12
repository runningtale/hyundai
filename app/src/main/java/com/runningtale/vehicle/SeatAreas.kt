package com.runningtale.vehicle

import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import com.runningtale.model.SeatPosition

/** 우리 모델의 뒷좌석 자리 ↔ Pleos 구역 */
internal fun SeatPosition.toSeatArea(): SeatArea = when (this) {
    SeatPosition.REAR_LEFT -> SeatArea(SeatValue.ROW_2_LEFT)
    SeatPosition.REAR_CENTER -> SeatArea(SeatValue.ROW_2_CENTER)
    SeatPosition.REAR_RIGHT -> SeatArea(SeatValue.ROW_2_RIGHT)
}

/** 뒷문은 좌우만 있음. 가운데 자리는 왼쪽 문으로 봄 */
internal fun SeatPosition.toDoorArea(): DoorArea = when (this) {
    SeatPosition.REAR_RIGHT -> DoorArea(DoorValue.ROW_2_RIGHT)
    else -> DoorArea(DoorValue.ROW_2_LEFT)
}
