package com.example.training

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView

/** Texture-backed teaching video so Compose lesson controls render over the player. */
internal class LessonVideoView(
    context: Context,
    private val source: Uri,
    private val muted: Boolean,
    private val onFailure: (String) -> Unit
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSurface: Surface? = null
    private var prepared = false
    private var firstFrameRendered = false
    private var videoWidth = 0
    private var videoHeight = 0

    var playbackEnabled: Boolean = true
        set(value) {
            field = value
            syncPlayback()
        }

    init {
        isOpaque = true
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        releasePlayer()
        try {
            val surface = Surface(texture)
            mediaSurface = surface
            val player = MediaPlayer()
            mediaPlayer = player
            player.setSurface(surface)
            player.setDataSource(context, source)
            player.isLooping = true
            player.setOnVideoSizeChangedListener { _, w, h ->
                videoWidth = w
                videoHeight = h
                fitVideoWithinView()
            }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    firstFrameRendered = true
                    Log.i(TAG, "Teaching video rendered first frame: $source")
                    syncPlayback()
                }
                false
            }
            player.setOnPreparedListener {
                prepared = true
                if (muted) it.setVolume(0f, 0f)
                Log.i(TAG, "Teaching video prepared: ${it.videoWidth}x${it.videoHeight}")
                syncPlayback()
            }
            player.setOnErrorListener { _, what, extra ->
                prepared = false
                Log.e(TAG, "Teaching video failed: what=$what extra=$extra source=$source")
                onFailure("教学视频播放失败（$what/$extra）")
                true
            }
            player.prepareAsync()
        } catch (error: Exception) {
            Log.e(TAG, "Teaching video setup failed: $source", error)
            onFailure("教学视频加载失败")
            releasePlayer()
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        fitVideoWithinView()
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    private fun fitVideoWithinView() {
        if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return
        // TextureView normally stretches a portrait video into this wide training panel.
        // Keep the source aspect ratio and center it without cropping the coach's arms.
        val viewAspect = width.toFloat() / height
        val videoAspect = videoWidth.toFloat() / videoHeight
        val transform = Matrix()
        if (videoAspect < viewAspect) {
            transform.setScale(videoAspect / viewAspect, 1f, width / 2f, height / 2f)
        } else {
            transform.setScale(1f, viewAspect / videoAspect, width / 2f, height / 2f)
        }
        setTransform(transform)
    }

    private fun syncPlayback() {
        val player = mediaPlayer ?: return
        if (!prepared) return
        try {
            // Start briefly to render a still when the user switches to a paused lesson.
            // Then freeze on that first frame instead of leaving an empty black panel.
            if ((playbackEnabled || !firstFrameRendered) && !player.isPlaying) player.start()
            if (!playbackEnabled && firstFrameRendered && player.isPlaying) player.pause()
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Unable to sync teaching video playback", error)
        }
    }

    fun release() {
        releasePlayer()
        surfaceTextureListener = null
    }

    private fun releasePlayer() {
        prepared = false
        firstFrameRendered = false
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
        mediaSurface?.release()
        mediaSurface = null
    }

    private companion object {
        const val TAG = "LITTA_LessonVideo"
    }
}
