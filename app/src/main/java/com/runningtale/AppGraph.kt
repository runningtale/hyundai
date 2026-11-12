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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** 객체 연결 (수동 DI). StoryMediaService 하나의 생명주기 동안만 존재. */
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
        narrator.start()
        naviGateway.start()
        // 차량 리스너 등록은 에뮬레이터에서 10초 넘게 걸려 메인 스레드에서 하면 서비스 시작이 막힘 → 백그라운드에서
        scope.launch(Dispatchers.IO) {
            vehicleGateway.start()
            // Vehicle.initialize()는 연결 콜백이 없어 등록 뒤 공조 상태를 읽음
            // TODO(담당 2): 연결 완료를 확인하는 방법으로 교체
            delay(1_000)
            effects.start()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        // TODO(담당 2): restoreAll의 복귀 명령은 비동기라 바로 release하면 끊길 수 있음. 복귀 완료 확인 후 release
        effects.restoreAll()
        vehicleGateway.stop()
        naviGateway.stop()
        narrator.release()
        scope.cancel()
    }
}
