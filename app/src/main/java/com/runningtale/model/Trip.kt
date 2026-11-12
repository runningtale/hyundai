package com.runningtale.model

/**
 * 목적지 종류. NaviHelper는 종류 정보를 주지 않으므로 장소 이름 키워드로 기기 안에서 분류.
 * BLOCKED: 허용 목록 밖(장례식장, 병원 등). 목적지 이름을 이야기에 쓰지 않음.
 */
enum class PlaceKind {
    SEA, MOUNTAIN, PARK, AMUSEMENT, FAMILY_HOME, HOME, RESTAURANT, LODGING, SCHOOL, OTHER, BLOCKED,
}

enum class TimeOfDay { MORNING, DAY, EVENING, NIGHT }

/** StoryDirector가 최신으로 유지하는 지금 여정의 상태. 이야기 생성과 효과 판단에 씀. */
data class TripState(
    val destinationName: String? = null,
    val placeKind: PlaceKind = PlaceKind.OTHER,
    val remainSec: Int? = null,
    val timeOfDay: TimeOfDay = TimeOfDay.DAY,
    val isRaining: Boolean = false,
    val isHeadlightOn: Boolean = false,
    val isMoving: Boolean = false,
)
