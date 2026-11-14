package com.runningtale.config

import android.content.Context
import com.runningtale.model.ChildProfile
import com.runningtale.model.EffectType
import com.runningtale.model.SeatPosition

/**
 * 설정 화면에서 등록한 아이 정보와 효과 켜기·끄기 (기기 안에만 저장, 매니페스트 allowBackup=false).
 * 등록 전에는 DemoPreset 값으로 동작해 첫 설치 상태에서도 바로 들을 수 있음.
 * 나이는 데모 이야기에 쓰이지 않아 화면에 두지 않고 DemoPreset 값을 그대로 씀 (docs/design/child-setup-arch.md)
 */
class ChildStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val isRegistered: Boolean get() = prefs.contains(KEY_NAME)

    fun load(): ChildProfile {
        val preset = DemoPreset.child
        val seat = prefs.getString(KEY_SEAT, null)
            ?.let { saved -> SeatPosition.entries.firstOrNull { it.name == saved } }
        return preset.copy(
            name = prefs.getString(KEY_NAME, null) ?: preset.name,
            seat = seat ?: preset.seat,
        )
    }

    fun isEffectEnabled(type: EffectType): Boolean = prefs.getBoolean(keyOf(type), true)

    fun save(name: String, seat: SeatPosition, fanEnabled: Boolean, lightEnabled: Boolean) {
        require(isValidName(name))
        prefs.edit()
            .putString(KEY_NAME, name)
            .putString(KEY_SEAT, seat.name)
            .putBoolean(keyOf(EffectType.FAN), fanEnabled)
            .putBoolean(keyOf(EffectType.READING_LIGHT), lightEnabled)
            .apply()
    }

    private fun keyOf(type: EffectType) = when (type) {
        EffectType.FAN -> KEY_FAN
        EffectType.READING_LIGHT -> KEY_LIGHT
    }

    companion object {
        private const val FILE = "child"
        private const val KEY_NAME = "name"
        private const val KEY_SEAT = "seat"
        private const val KEY_FAN = "fan_enabled"
        private const val KEY_LIGHT = "light_enabled"

        /** 한글 완성형 1~6자. 받침 판정(Josa)이 항상 되도록 */
        private val NAME = Regex("[가-힣]{1,6}")

        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}
