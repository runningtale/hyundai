package com.runningtale.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.runningtale.AppGraph
import com.runningtale.R
import com.runningtale.RunningTaleApp
import kotlinx.coroutines.launch

/**
 * 차 기본 미디어 화면과 핸들 버튼으로 조작하는 진입점 (담당 3).
 * 재생 화면은 없고 주차 중 아이 등록 화면(SettingsActivity)만 있음. Pleos 오디오 앱 요건: MediaSession, 핸들 미디어 키, 오디오 포커스, 자동 재생 금지.
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

    private val app get() = application as RunningTaleApp
    private lateinit var graph: AppGraph
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
        graph = AppGraph(applicationContext).also {
            app.graph = it
            it.start()
        }

        // session에 먼저 대입한 뒤 setState 호출 (setState가 session을 씀)
        session = MediaSessionCompat(this, TAG)
        session.setCallback(callback)
        session.setMetadata(metadataOf(NowPlaying()))
        setState(PlaybackStateCompat.STATE_PAUSED)
        session.isActive = true
        sessionToken = session.sessionToken

        // 장이 바뀌면 재생 카드, 목적지가 바뀌면 목록 부제 갱신
        graph.scope.launch {
            director.nowPlaying.collect {
                session.setMetadata(metadataOf(it))
                // 결말까지 다 읽으면 버튼을 재생(▶)으로 돌려 "다시 듣기"가 보이게 함
                if (it.finished) setState(PlaybackStateCompat.STATE_PAUSED)
            }
        }
        graph.scope.launch { director.destinationLabel.collect { notifyChildrenChanged(TAB_ID) } }
    }

    /** 재생 카드: 제목 = 장 제목, 부제 = 이야기 제목, 그림 = 기본 그림 (장면 그림은 docs/07 4-3절, P1) */
    private fun metadataOf(now: NowPlaying): MediaMetadataCompat {
        val (title, subtitle) = when {
            now.finished -> "도착했어요" to (now.storyTitle ?: APP_TITLE)
            now.chapterTitle != null -> "${now.chapterIndex}장. ${now.chapterTitle}" to (now.storyTitle ?: APP_TITLE)
            else -> APP_TITLE to DEFAULT_SUBTITLE
        }
        return MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            // Pleos 미디어 앱은 비트맵이 아니라 주소(URI)로 그림을 읽음 (에뮬레이터 확인). 다른 화면을 위해 비트맵도 같이 넣음
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri)
            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
            .build()
    }

    /** 재생 카드·목록 그림 주소. 주소로 읽히려면 그림이 PNG여야 함 (벡터 XML은 주소로 못 읽음) */
    private val artUri by lazy { "android.resource://$packageName/drawable/art_default" }

    /** 재생 카드 그림 비트맵. 세션 메타데이터는 프레임워크가 크기를 줄여 넘김 */
    private val art: Bitmap by lazy { render(R.drawable.art_default, ART_SIZE_PX) }

    private fun render(resId: Int, sizePx: Int): Bitmap {
        val drawable = checkNotNull(ContextCompat.getDrawable(this, resId))
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(it))
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    /**
     * Pleos 미디어 앱은 루트의 자식을 "탭"으로 보여 줌.
     * 그래서 루트 → 탭(이야기, BROWSABLE) → 재생 항목(출발하며 듣기, PLAYABLE) 2단으로 둠
     */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        Log.d(TAG, "onLoadChildren $parentId")
        val items = when (parentId) {
            ROOT_ID -> mutableListOf(
                MediaItem(
                    MediaDescriptionCompat.Builder().setMediaId(TAB_ID).setTitle("이야기").build(),
                    MediaItem.FLAG_BROWSABLE,
                ),
            )
            TAB_ID -> mutableListOf(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(START_ID)
                        .setTitle("출발하며 듣기")
                        .setSubtitle(director.destinationLabel.value?.let { "$it 가는 길 이야기" } ?: DEFAULT_SUBTITLE)
                        .setIconUri(Uri.parse(artUri))
                        .build(),
                    MediaItem.FLAG_PLAYABLE,
                ),
            )
            else -> mutableListOf()
        }
        result.sendResult(items)
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d(TAG, "onPlay")
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "오디오 포커스 거부")
                return
            }
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
                director.resume()
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
        app.graph = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "StoryMediaService"
        const val APP_TITLE = "달리는 동화"
        const val DEFAULT_SUBTITLE = "이번 여정이 이야기가 돼요"
        const val ART_SIZE_PX = 512
        const val ROOT_ID = "root"
        const val TAB_ID = "stories"
        const val START_ID = "start"
    }
}
