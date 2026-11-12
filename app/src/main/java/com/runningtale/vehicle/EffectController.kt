package com.runningtale.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.listener.HvacAutoStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import android.os.SystemClock
import android.util.Log
import com.runningtale.config.AppConfig
import com.runningtale.model.Effect
import com.runningtale.model.EffectType
import com.runningtale.model.SeatPosition
import com.runningtale.model.TripState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 4D 효과 실행과 안전 규칙 (담당 2). docs/03_모듈간_약속.md 4장.
 *
 * 규칙
 * - 운전석 온도·시트는 건드리지 않음
 * - 독서등: 아이 자리만, 전조등 켜짐이면 실행 안 함, 최대 30초
 * - 풍량: 공조 꺼짐이면 실행 안 함, +1단, 최대 60초, 끝나면 원래 값(AUTO 포함)으로
 * - 같은 종류가 실행 중이면 새 요청 무시
 * - 같은 종류는 마지막 실행 후 EFFECT_COOLDOWN_SEC 동안 다시 실행 안 함
 *
 * TODO(담당 2): 주행 규제(driving_regulation on) 상태에서 set 호출이 되는지 확인하고 결과를 docs/05에 기록
 */
class EffectController(
    private val vehicle: Vehicle,
    private val scope: CoroutineScope,
) {
    private var fanArea: SeatArea? = null
    private var autoArea: SeatArea? = null
    private var fanMax: Int = 8
    private var hvacOn: Boolean = false
    private var hvacAuto: Boolean = false
    private var currentFan: Int? = null

    private var fanJob: Job? = null
    private var fanRestore: Pair<Int, Boolean>? = null // (원래 풍량, 원래 AUTO)
    private val lightJobs = mutableMapOf<SeatPosition, Job>()
    private val lastStartedAt = mutableMapOf<EffectType, Long>()

    /**
     * 공조 구역과 현재 상태를 읽어 캐시. 효과 판단은 이 캐시로 동기 처리.
     * 풍량, 전원, AUTO는 지원 구역이 서로 다를 수 있어 각각 조회함
     * (에뮬레이터: 풍량은 앞좌석 묶음 0x5, 전원은 다른 구역 → 같은 구역으로 읽으면 IllegalArgumentException)
     * 리스너는 값이 바뀔 때만 오므로 현재 값을 한 번 읽어 캐시를 채움
     */
    fun start() {
        val hvac = vehicle.getHVAC()
        hvac.checkHvacFanSpeedCapabilities({ areas ->
            val (area, bounds) = areas.entries.firstOrNull() ?: return@checkHvacFanSpeedCapabilities
            fanArea = area
            fanMax = bounds.maxValue ?: fanMax
            Log.i(TAG, "풍량 구역 ${area.values.toList()}, 최대 $fanMax")
            hvac.registerHvacFanSpeed(area, fanListener)
            hvac.getHvacFanSpeed(area, { currentFan = it; Log.i(TAG, "현재 풍량 $it") }, { Log.w(TAG, "풍량 읽기 실패", it) })
        }, { Log.w(TAG, "풍량 구역 조회 실패", it) })

        hvac.checkHvacPowerCapabilities({ areas ->
            val area = areas.firstOrNull() ?: return@checkHvacPowerCapabilities
            hvac.registerHvacPowerState(area, powerListener)
            hvac.getHvacPowerState(area, { hvacOn = it == true; Log.i(TAG, "공조 전원 $it") }, { Log.w(TAG, "공조 전원 읽기 실패", it) })
        }, { Log.w(TAG, "공조 전원 구역 조회 실패", it) })

        hvac.checkHvacAutoCapabilities({ areas ->
            val area = areas.firstOrNull() ?: return@checkHvacAutoCapabilities
            autoArea = area
            hvac.registerHvacAutoState(area, autoListener)
            hvac.getHvacAutoState(area, { hvacAuto = it == true }, { Log.w(TAG, "AUTO 읽기 실패", it) })
        }, { Log.w(TAG, "AUTO 구역 조회 실패", it) })
    }

    private val powerListener = object : HvacPowerStateListener {
        override fun onHvacPowerStateUpdated(on: Boolean?) { hvacOn = on == true }
        override fun onFailed(e: Exception) { Log.w(TAG, "공조 전원 구독 실패", e) }
    }
    private val autoListener = object : HvacAutoStateListener {
        override fun onHvacAutoStateUpdated(on: Boolean?) { hvacAuto = on == true }
        override fun onFailed(e: Exception) { Log.w(TAG, "AUTO 구독 실패", e) }
    }
    private val fanListener = object : HvacFanSpeedListener {
        override fun onHvacFanSpeedUpdated(speed: Int?) { currentFan = speed }
        override fun onFailed(e: Exception) { Log.w(TAG, "풍량 구독 실패", e) }
    }

    /** @return 실행했으면 true, 규칙에 걸리면 false */
    fun apply(effect: Effect, seat: SeatPosition, trip: TripState): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastStartedAt[effect.type]
        if (last != null && now - last < AppConfig.EFFECT_COOLDOWN_SEC * 1000L) {
            Log.d(TAG, "${effect.type} 쿨다운 중, 건너뜀")
            return false
        }
        val started = when (effect.type) {
            EffectType.READING_LIGHT -> readingLight(seat, effect.durationSec, trip)
            EffectType.FAN -> fan(effect.durationSec)
        }
        if (started) lastStartedAt[effect.type] = now
        Log.i(TAG, "${effect.type} ${effect.durationSec}초 ${if (started) "실행" else "조건 안 맞아 건너뜀"}")
        return started
    }

    private fun readingLight(seat: SeatPosition, durationSec: Int, trip: TripState): Boolean {
        // 주행 중(D)에는 차량이 독서등 제어를 거부함 (에뮬레이터 주행 상태에서 IllegalArgumentException 50001 확인)
        if (trip.isHeadlightOn || trip.isMoving) return false
        if (lightJobs[seat]?.isActive == true) return false
        val area = seat.toSeatArea()
        val sec = durationSec.coerceAtMost(AppConfig.READING_LIGHT_MAX_SEC)
        vehicle.getLight().setReadingLightSwitch(area, LightSwitch.ON, {}, { Log.w(TAG, "독서등 켜기 실패", it) })
        lightJobs[seat] = scope.launch {
            delay(sec * 1000L)
            vehicle.getLight().setReadingLightSwitch(area, LightSwitch.OFF, {}, { Log.w(TAG, "독서등 끄기 실패", it) })
        }
        return true
    }

    private fun fan(durationSec: Int): Boolean {
        val area = fanArea ?: return false
        val now = currentFan ?: return false
        if (!hvacOn || fanJob?.isActive == true) return false
        val target = (now + 1).coerceAtMost(fanMax)
        if (target == now) return false
        fanRestore = now to hvacAuto
        vehicle.getHVAC().setHvacFanSpeed(area, target, { _, _ -> }, { Log.w(TAG, "풍량 변경 실패", it) })
        fanJob = scope.launch {
            delay(durationSec.coerceAtMost(AppConfig.FAN_MAX_SEC) * 1000L)
            restoreFan()
        }
        return true
    }

    private fun restoreFan() {
        val area = fanArea ?: return
        val (speed, auto) = fanRestore ?: return
        vehicle.getHVAC().setHvacFanSpeed(area, speed, { _, _ -> }, { Log.w(TAG, "풍량 복귀 실패", it) })
        val aArea = autoArea
        if (auto && aArea != null) vehicle.getHVAC().setHvacAuto(aArea, true, { _, _ -> }, { Log.w(TAG, "AUTO 복귀 실패", it) })
        fanRestore = null
    }

    /** 바꾼 값 전부 원래대로 (도착, 일시정지, 종료 시) */
    fun restoreAll() {
        lightJobs.forEach { (seat, job) ->
            job.cancel()
            vehicle.getLight().setReadingLightSwitch(seat.toSeatArea(), LightSwitch.OFF, {}, {})
        }
        lightJobs.clear()
        fanJob?.cancel()
        restoreFan()
    }

    private companion object {
        const val TAG = "EffectController"
    }
}
