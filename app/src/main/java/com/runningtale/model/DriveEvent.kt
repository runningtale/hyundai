package com.runningtale.model

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }

/**
 * VehicleGateway, NaviGateway가 내보내고 StoryDirector가 받는 주행 이벤트.
 * 목록과 출처 API는 docs/03_모듈간_약속.md 2장.
 */
sealed interface DriveEvent {
    data class GearChanged(val gear: Gear) : DriveEvent
    data class RearSeatChanged(val seat: SeatPosition, val occupied: Boolean) : DriveEvent
    data class RearDoorChanged(val seat: SeatPosition, val open: Boolean) : DriveEvent
    data class RainChanged(val raining: Boolean) : DriveEvent
    data class HeadlightChanged(val on: Boolean) : DriveEvent
    data class DestinationUpdated(val name: String, val remainSec: Int) : DriveEvent

    /** 터널, 요금소, 휴게소 예고. trigger는 TUNNEL, TOLL, REST_AREA 중 하나. */
    data class RoadEventAhead(val trigger: InterludeTrigger, val distanceM: Int) : DriveEvent
    data object Arrived : DriveEvent
}
