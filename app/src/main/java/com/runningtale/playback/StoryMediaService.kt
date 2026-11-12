package com.runningtale.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.runningtale.RunningTaleApp

/**
 * 차 기본 미디어 화면과 핸들 버튼으로 조작하는 진입점 (담당 3).
 * 앱 전용 화면은 없음. Pleos 오디오 앱 요건: MediaSession, 핸들 미디어 키, 오디오 포커스, 자동 재생 금지.
 *
 * TODO(담당 3):
 *  - 재생 중 포그라운드 서비스와 알림 (장시간 재생 시 종료 방지)
 *  - 다음 장 건너뛰기(onSkipToNext)
 *  - 내비 안내(포커스 일시 손실)에서 멈췄다가 자동 재개되는지 에뮬레이터에서 확인
 */
class StoryMediaService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var resumeOnFocusGain = false

    private val graph get() = (application as RunningTaleApp).graph
    private val director get() = graph.director

    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(::onFocusChange)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        graph.start()

        session = MediaSessionCompat(this, TAG).apply {
            setCallback(callback)
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, TITLE)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "이번 여정의 이야기")
                    .build(),
            )
            setState(PlaybackStateCompat.STATE_PAUSED)
            isActive = true
        }
        sessionToken = session.sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        val item = MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(START_ID)
                .setTitle("출발하며 듣기")
                .setSubtitle(TITLE)
                .build(),
            MediaItem.FLAG_PLAYABLE,
        )
        result.sendResult(if (parentId == ROOT_ID) mutableListOf(item) else mutableListOf())
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            director.play()
            setState(PlaybackStateCompat.STATE_PLAYING)
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = onPlay()

        override fun onPause() {
            director.pause()
            setState(PlaybackStateCompat.STATE_PAUSED)
        }

        override fun onStop() {
            director.pause()
            audioManager.abandonAudioFocusRequest(focusRequest)
            setState(PlaybackStateCompat.STATE_STOPPED)
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                // 내비 안내, Gleo 호출: 멈추고 효과 복귀, 끝나면 재개
                resumeOnFocusGain = director.isPlaying
                callback.onPause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                callback.onStop()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) {
                resumeOnFocusGain = false
                director.play()
                setState(PlaybackStateCompat.STATE_PLAYING)
            }
        }
    }

    private fun setState(state: Int) {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID,
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    override fun onDestroy() {
        director.pause()
        audioManager.abandonAudioFocusRequest(focusRequest)
        session.release()
        graph.stop()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "StoryMediaService"
        const val TITLE = "달리는 동화"
        const val ROOT_ID = "root"
        const val START_ID = "start"
    }
}
