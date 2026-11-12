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
 * TODO(담당 3):
 *  - speak의 두 번째 인자 의미 확인 (지금은 문서 예시대로 false)
 *  - 효과음, 배경음 재생 추가 (라이선스 확인된 파일만)
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

    fun start() {
        tts.addEventListener(listener)
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                isReady = true
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "tts init 실패 $errorType")
            }
        })
    }

    /** 한 문장을 다 읽으면 반환. 실패하거나 너무 오래 걸리면 그냥 넘어감 */
    suspend fun speak(text: String, voice: Voice) {
        if (!isReady) return
        val done = CompletableDeferred<Unit>()
        current = done
        tts.setSpeakerType(voice.pleosId)
        tts.speak(text, false)
        withTimeoutOrNull(MAX_LINE_MS) { done.await() }
    }

    fun stop() {
        tts.stop()
        finish()
    }

    fun release() {
        tts.removeEventListener(listener)
        tts.release()
    }

    private fun finish() {
        current?.complete(Unit)
        current = null
    }

    private companion object {
        const val TAG = "Narrator"
        const val MAX_LINE_MS = 20_000L
    }
}
