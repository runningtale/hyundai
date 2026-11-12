package com.runningtale

import android.content.Context
import com.runningtale.config.AppConfig
import com.runningtale.config.DemoPreset
import com.runningtale.navi.NaviGateway
import com.runningtale.playback.Narrator
import com.runningtale.playback.StoryDirector
import com.runningtale.story.DemoStoryGenerator
import com.runningtale.story.LlmStoryGenerator
import com.runningtale.story.NotConfiguredLlmClient
import com.runningtale.story.PromptBuilder
import com.runningtale.story.SafetyFilter
import com.runningtale.story.StoryGenerator
import com.runningtale.story.StoryParser
import com.runningtale.vehicle.EffectController
import com.runningtale.vehicle.VehicleGateway
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** 객체 연결 (수동 DI). 앱 프로세스에 하나. */
class AppGraph(context: Context) {

    val scope = MainScope()

    val vehicleGateway = VehicleGateway(context)
    val naviGateway = NaviGateway(context, scope)
    val effects = EffectController(vehicleGateway.vehicle, scope)
    val narrator = Narrator(context)
    private val safety = SafetyFilter()

    private val generator: StoryGenerator =
        if (AppConfig.isDemoMode) {
            DemoStoryGenerator(safety)
        } else {
            LlmStoryGenerator(NotConfiguredLlmClient(), PromptBuilder(), StoryParser(), safety)
        }

    val director = StoryDirector(
        scope = scope,
        generator = generator,
        narrator = narrator,
        effects = effects,
        safety = safety,
        child = DemoPreset.child,
        events = merge(vehicleGateway.events, naviGateway.events),
    )

    private var started = false

    fun start() {
        if (started) return
        started = true
        vehicleGateway.start()
        naviGateway.start()
        narrator.start()
        // Vehicle.initialize()는 연결 콜백이 없어 잠시 뒤 공조 상태를 읽음
        // TODO(담당 2): 연결 완료를 확인하는 방법으로 교체
        scope.launch {
            delay(2_000)
            effects.start()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        effects.restoreAll()
        vehicleGateway.stop()
        naviGateway.stop()
        narrator.release()
        scope.cancel()
    }
}
