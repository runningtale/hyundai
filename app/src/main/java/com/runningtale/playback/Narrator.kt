package com.runningtale.playback

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import com.runningtale.model.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pleos TTS 낭독 (담당 3). ON_DEVICE 모드라 앱 등록 없이 동작.
 *
 * speak(text, true): 두 번째 인자의 SDK 기본값은 true. false를 주면 ON_DEVICE 스트림이
 * 다음 문장을 기다리며 끝나지 않아 onDone이 오지 않음 (에뮬레이터에서 확인)
 *
 * TODO(담당 3): 효과음, 배경음 재생 추가 (라이선스 확인된 파일만)
 */
class Narrator(context: Context) {

    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    private var current: CompletableDeferred<Unit>? = null
    var isReady: Boolean = false
        private set

    private val listener = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() { finish() }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "tts error $errorType")
            finish()
        }
        override fun onStop() { finish() }
        override fun onUpdatedRms(rms: Double) {}
    }

    private val ready = CompletableDeferred<Boolean>()

    /** 리스너는 초기화가 끝난 뒤에 붙여야 함 (먼저 붙이면 SDK 내부 lateinit 오류로 앱이 죽음) */
    fun start() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(listener)
                tts.setLocale(Locale.KOREA)
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                isReady = true
                ready.complete(true)
                Log.i(TAG, "tts 준비 완료")
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "tts init 실패 $errorType")
                ready.complete(false)
            }
        })
    }

    /** 한 문장을 다 읽으면 반환. 초기화를 최대 10초 기다리고, 실패하거나 너무 오래 걸리면 그냥 넘어감 */
    suspend fun speak(text: String, voice: Voice) {
        if (withTimeoutOrNull(INIT_WAIT_MS) { ready.await() } != true) return
        Log.d(TAG, "speak[${voice.pleosId}] $text")
        val done = CompletableDeferred<Unit>()
        current = done
        tts.setSpeakerType(voice.pleosId)
        tts.speak(text, true)
        withTimeoutOrNull(MAX_LINE_MS) { done.await() }
    }

    fun stop() {
        if (isReady) tts.stop()
        finish()
    }

    fun release() {
        if (isReady) tts.removeEventListener(listener)
        tts.release()
    }

    private fun finish() {
        current?.complete(Unit)
        current = null
    }

    private companion object {
        const val TAG = "Narrator"
        const val MAX_LINE_MS = 20_000L
        const val INIT_WAIT_MS = 10_000L
    }
}
