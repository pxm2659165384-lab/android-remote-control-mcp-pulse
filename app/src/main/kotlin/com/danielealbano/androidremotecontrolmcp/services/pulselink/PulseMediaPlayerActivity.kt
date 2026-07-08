package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.app.Activity
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView

class PulseMediaPlayerActivity : Activity() {
    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) {
            showMessage("没有可播放的媒体")
            return
        }

        title = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { "Pulse Link 媒体跳转" }
        videoView =
            VideoView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                setMediaController(MediaController(this@PulseMediaPlayerActivity).also { it.setAnchorView(this) })
                setVideoURI(uri)
                setOnPreparedListener { player ->
                    startFromConfiguredPosition(player, uri)
                }
                setOnErrorListener { _, _, _ ->
                    showMessage("媒体播放失败")
                    true
                }
            }
        setContentView(videoView)
    }

    override fun onStop() {
        runCatching { videoView.stopPlayback() }
        super.onStop()
    }

    private fun startFromConfiguredPosition(
        player: MediaPlayer,
        uri: Uri,
    ) {
        val requestedMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        val targetMs = PulseMediaStartPosition.resolve(requestedMs, player.duration)
        if (targetMs <= 0) {
            videoView.start()
            PulseLogger.i("内置媒体播放器已启动 uri=$uri startMs=0")
            return
        }
        var started = false
        val startAfterSeek = {
            if (!started) {
                started = true
                videoView.start()
                PulseLogger.i("内置媒体播放器已跳转启动 uri=$uri startMs=$targetMs")
            }
        }
        player.setOnSeekCompleteListener {
            startAfterSeek()
        }
        videoView.seekTo(targetMs)
        Handler(Looper.getMainLooper()).postDelayed(startAfterSeek, SEEK_FALLBACK_DELAY_MS)
    }

    private fun showMessage(message: String) {
        val view =
            TextView(this).apply {
                text = message
                gravity = Gravity.CENTER
                textSize = 18f
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        setContentView(view)
    }

    companion object {
        const val EXTRA_START_POSITION_MS = "pulse_start_position_ms"
        const val EXTRA_DISPLAY_NAME = "pulse_display_name"
        private const val SEEK_FALLBACK_DELAY_MS = 900L
    }
}
