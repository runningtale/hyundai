package com.runningtale.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log
import com.runningtale.model.DriveEvent
import com.runningtale.model.Gear
import com.runningtale.model.SeatPosition
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 차량 상태 → DriveEvent (담당 2). docs/03_모듈간_약속.md 2장.
 *
 * TODO(담당 2):
 *  - 에뮬레이터에서 각 listener가 실제로 호출되는지 확인 (Virtual Console로 값 변경)
 *  - check…Capabilities로 지원 구역 확인 후 등록
 *  - 차속(CarPropertyManager PERF_VEHICLE_SPEED)은 2차
 */
class VehicleGateway(context: Context) {

    val vehicle: Vehicle = Vehicle(context)

    private val _events = MutableSharedFlow<DriveEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DriveEvent> = _events.asSharedFlow()

    private val rearSeats = listOf(SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT)
    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            emit(DriveEvent.GearChanged(gear.toGear()))
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "기어 구독 실패", e) }
    }

    private val seatListeners = rearSeats.associateWith { seat ->
        object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupied: Boolean?) {
                emit(DriveEvent.RearSeatChanged(seat, occupied == true))
            }
            override fun onFailed(e: Exception) { Log.w(TAG, "착석 구독 실패 $seat", e) }
        }
    }

    private val doorListeners = rearSeats.associateWith { seat ->
        object : DoorPositionListener {
            override fun onPositionUpdated(position: Int?) {
                emit(DriveEvent.RearDoorChanged(seat, (position ?: 0) > 0))
            }
            override fun onFailed(e: Exception) { Log.w(TAG, "도어 구독 실패 $seat", e) }
        }
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            emit(DriveEvent.RainChanged(state == WiperState.ON))
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "와이퍼 구독 실패", e) }
    }

    private val headlightListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            emit(DriveEvent.HeadlightChanged(state == LightState.ON))
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "전조등 구독 실패", e) }
    }

    /** 하나가 실패해도(퍼미션, 미지원 차종) 나머지는 계속 등록. 실패는 로그로만 */
    fun start() {
        vehicle.initialize()
        safely("기어") { vehicle.getGear().registerGearSelection(gearListener) }
        seatListeners.forEach { (seat, l) -> safely("착석 $seat") { vehicle.getSeat().registerSeatOccupancy(seat.toSeatArea(), l) } }
        doorListeners.forEach { (seat, l) -> safely("도어 $seat") { vehicle.getDoor().registerDoorPosition(seat.toDoorArea(), l) } }
        safely("와이퍼") { vehicle.getWiper().registerWiperState(frontWindshield, wiperListener) }
        safely("전조등") { vehicle.getLight().registerLowBeamLightState(headlightListener) }
    }

    fun stop() {
        safely("기어 해제") { vehicle.getGear().unregisterGearSelection(gearListener) }
        seatListeners.forEach { (seat, l) -> safely("착석 해제") { vehicle.getSeat().unregisterSeatOccupancy(seat.toSeatArea(), l) } }
        doorListeners.forEach { (seat, l) -> safely("도어 해제") { vehicle.getDoor().unregisterDoorPosition(seat.toDoorArea(), l) } }
        safely("와이퍼 해제") { vehicle.getWiper().unregisterWiperState(frontWindshield, wiperListener) }
        safely("전조등 해제") { vehicle.getLight().unregisterLowBeamLightState(headlightListener) }
        vehicle.release()
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "$what 등록 실패", e)
        }
    }

    /** 시연용 이벤트 주입(debug)도 이 경로로 들어옴 */
    fun emit(event: DriveEvent) {
        Log.d(TAG, "event $event")
        _events.tryEmit(event)
    }

    private fun VehicleGear?.toGear(): Gear = when (this) {
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        else -> Gear.DRIVE // GEAR_DRIVE, GEAR_1~9
    }

    private companion object {
        const val TAG = "VehicleGateway"
    }
}
