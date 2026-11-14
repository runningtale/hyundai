package com.runningtale.settings

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import com.runningtale.R
import com.runningtale.RunningTaleApp
import com.runningtale.config.ChildStore
import com.runningtale.model.DriveEvent
import com.runningtale.model.EffectType
import com.runningtale.model.Gear
import com.runningtale.model.SeatPosition
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 주차 중 아이 등록 화면. 미디어 화면 "⋯" 메뉴의 앱 설정(APPLICATION_PREFERENCES)으로 열림.
 * 주행 중 사용 선언(distractionOptimized)은 없지만 Pleos 에뮬레이터는 D + 주행 중에도 이 화면을 막지 않았음 (11/14 확인)
 * → 기어가 D이면 열지 않고, 열려 있다가 D가 되면 닫음 (StoryDirector의 주행 판단 trip.isMoving과 같은 기준).
 *   기어는 서비스(AppGraph)가 받는 값을 씀. 미디어 화면에서 열면 서비스가 항상 있음. 서비스가 없으면 확인하지 않음
 * 바뀐 이름과 자리는 다음 이야기부터, 효과 켜기·끄기는 바로 반영 (docs/design/child-setup-arch.md)
 */
class SettingsActivity : Activity() {

    private lateinit var store: ChildStore
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = ChildStore(this)
        if (closeWhileDriving()) return

        val name = findViewById<EditText>(R.id.name)
        val seat = findViewById<RadioGroup>(R.id.seat)
        val fan = findViewById<Switch>(R.id.fan)
        val light = findViewById<Switch>(R.id.light)

        val child = store.load()
        if (store.isRegistered) name.setText(child.name)
        seat.check(if (child.seat == SeatPosition.REAR_RIGHT) R.id.seat_right else R.id.seat_left)
        fan.isChecked = store.isEffectEnabled(EffectType.FAN)
        light.isChecked = store.isEffectEnabled(EffectType.READING_LIGHT)

        findViewById<Button>(R.id.save).setOnClickListener {
            val typed = name.text.toString().trim()
            if (!ChildStore.isValidName(typed)) {
                name.error = getString(R.string.settings_name_error)
                return@setOnClickListener
            }
            val position = if (seat.checkedRadioButtonId == R.id.seat_right) SeatPosition.REAR_RIGHT else SeatPosition.REAR_LEFT
            store.save(typed, position, fan.isChecked, light.isChecked)
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** 이미 D이면 바로 닫고 true. 아니면 D로 바뀔 때 닫도록 구독 */
    private fun closeWhileDriving(): Boolean {
        val graph = (application as RunningTaleApp).graph ?: return false
        if (graph.director.trip.isMoving) {
            closeForDriving()
            return true
        }
        scope.launch {
            graph.vehicleGateway.events.filterIsInstance<DriveEvent.GearChanged>()
                .first { it.gear == Gear.DRIVE }
            closeForDriving()
        }
        return false
    }

    private fun closeForDriving() {
        Toast.makeText(this, R.string.settings_driving, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
