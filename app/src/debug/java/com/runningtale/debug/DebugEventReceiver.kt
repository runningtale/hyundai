package com.runningtale.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.runningtale.RunningTaleApp
import com.runningtale.model.DriveEvent
import com.runningtale.model.Gear
import com.runningtale.model.InterludeTrigger

/**
 * 시연용 이벤트 주입 (debug 빌드에만 포함, 담당 2).
 * 모의 주행을 앱에서 시작할 수 없어서 터널, 목적지 등을 adb로 넣음.
 *
 * 예시 (README 시연 절 참고):
 *   adb shell am broadcast -n com.runningtale/.debug.DebugEventReceiver -a com.runningtale.DEBUG_EVENT --es type tunnel --ei distance 800
 *   --es type tunnel|toll|rest_area   --ei distance <m>
 *   --es type rain        --ez on true|false
 *   --es type headlight   --ez on true|false
 *   --es type destination --es name "강릉 경포해변" --ei remain <초>
 *   --es type gear        --es value D|P
 *   --es type arrived
 */
class DebugEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as RunningTaleApp).graph
        val event = parse(intent) ?: run {
            Log.w(TAG, "알 수 없는 type: ${intent.getStringExtra("type")}")
            return
        }
        graph.vehicleGateway.emit(event)
    }

    private fun parse(i: Intent): DriveEvent? = when (i.getStringExtra("type")) {
        "tunnel" -> DriveEvent.RoadEventAhead(InterludeTrigger.TUNNEL, i.getIntExtra("distance", 0))
        "toll" -> DriveEvent.RoadEventAhead(InterludeTrigger.TOLL, i.getIntExtra("distance", 0))
        "rest_area" -> DriveEvent.RoadEventAhead(InterludeTrigger.REST_AREA, i.getIntExtra("distance", 0))
        "rain" -> DriveEvent.RainChanged(i.getBooleanExtra("on", true))
        "headlight" -> DriveEvent.HeadlightChanged(i.getBooleanExtra("on", true))
        "destination" -> DriveEvent.DestinationUpdated(
            i.getStringExtra("name") ?: "목적지",
            i.getIntExtra("remain", 1800),
        )
        "gear" -> DriveEvent.GearChanged(if (i.getStringExtra("value") == "P") Gear.PARK else Gear.DRIVE)
        "arrived" -> DriveEvent.Arrived
        else -> null
    }

    private companion object {
        const val TAG = "DebugEventReceiver"
    }
}
