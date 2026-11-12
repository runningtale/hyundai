package com.runningtale.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.runningtale.config.AppConfig
import com.runningtale.model.DriveEvent
import com.runningtale.model.InterludeTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 내비 정보 → DriveEvent (담당 2). docs/03_모듈간_약속.md 2장.
 *
 * - 목적지 이름과 남은 시간: getDestinationInfo (종류 필드는 없음)
 * - 터널·요금소·휴게소 예고: getTBTInfo의 TurnType + 남은 거리
 *
 * TODO(담당 2):
 *  - onTBTInfo가 주행 중 자동으로 오는지, 요청해야만 오는지 에뮬레이터에서 확인 (지금은 주기 요청)
 *  - 모의 주행은 앱에서 시작할 수 없음 → 시연은 debug 이벤트 주입
 */
class NaviGateway(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val navi = NaviHelper(context)

    private val _events = MutableSharedFlow<DriveEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DriveEvent> = _events.asSharedFlow()

    private var pollJob: Job? = null

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            // 경로 안내가 없으면 빈 이름과 0초가 옴 (에뮬레이터 확인). 이걸 그대로 넘기면 이미 알던 목적지를 지우므로 건너뜀
            if (destinationInfo.name.isBlank()) return
            _events.tryEmit(DriveEvent.DestinationUpdated(destinationInfo.name, destinationInfo.remainTime))
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            tbtInfoList.forEach { tbt ->
                val trigger = tbt.type.toTrigger() ?: return@forEach
                if (tbt.distance <= AppConfig.ROAD_EVENT_PREPARE_M) {
                    _events.tryEmit(DriveEvent.RoadEventAhead(trigger, tbt.distance.toInt()))
                }
            }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _events.tryEmit(DriveEvent.Arrived)
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    fun start() {
        navi.addListener(listener)
        navi.initialize({ startPolling() }, { Log.w(TAG, "navi 연결 실패", it) })
    }

    fun stop() {
        pollJob?.cancel()
        navi.removeListener(listener)
        navi.release()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                navi.getDestinationInfo()
                navi.getTBTInfo()
                delay(POLL_MS)
            }
        }
    }

    private fun TurnType.toTrigger(): InterludeTrigger? = when (this) {
        TurnType.TURN_TUNNEL -> InterludeTrigger.TUNNEL
        TurnType.TURN_TOLL -> InterludeTrigger.TOLL
        TurnType.TURN_ENTER_SERVICEAREA, TurnType.TURN_RESTAREA -> InterludeTrigger.REST_AREA
        else -> null
    }

    private companion object {
        const val TAG = "NaviGateway"
        const val POLL_MS = 5_000L
    }
}
