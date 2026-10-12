package com.example.training

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

private const val SKIP_MS = 10_000
private val ControlOrange = Color(0xFFFF6418)

@Composable
internal fun ReplayVideoPlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var prepared by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    var positionMs by remember(file) { mutableIntStateOf(0) }
    var durationMs by remember(file) { mutableIntStateOf(0) }
    var scrubbing by remember(file) { mutableStateOf(false) }
    var scrubMs by remember(file) { mutableIntStateOf(0) }
    var error by remember(file) { mutableStateOf(false) }

    val videoView = remember(file) {
        VideoView(context).apply {
            setOnPreparedListener { player ->
                player.isLooping = false
                durationMs = player.duration.coerceAtLeast(0)
                prepared = true
                start()
                playing = true
            }
            setOnCompletionListener {
                playing = false
                positionMs = durationMs
            }
            setOnErrorListener { _, _, _ ->
                error = true
                playing = false
                true
            }
            setVideoURI(Uri.fromFile(file))
        }
    }

    DisposableEffect(videoView) {
        onDispose { videoView.stopPlayback() }
    }
    LaunchedEffect(videoView) {
        while (true) {
            if (prepared && !error) {
                durationMs = videoView.duration.coerceAtLeast(durationMs)
                if (!scrubbing && videoView.isPlaying) {
                    positionMs = videoView.currentPosition.coerceAtLeast(0)
                }
            }
            delay(200)
        }
    }

    Column(modifier = modifier.background(Color.Black)) {
        AndroidView(factory = { videoView }, modifier = Modifier.weight(1f).fillMaxWidth())
        if (error) Text("录像无法播放", color = Color.White)
        Slider(
            value = (if (scrubbing) scrubMs else positionMs).coerceIn(0, durationMs.coerceAtLeast(1)).toFloat(),
            onValueChange = { scrubMs = it.toInt(); scrubbing = true },
            onValueChangeFinished = {
                videoView.seekTo(scrubMs)
                positionMs = scrubMs
                scrubbing = false
            },
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
            enabled = prepared && durationMs > 0 && !error,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatReplayTime(if (scrubbing) scrubMs else positionMs), color = Color.White)
            Text(formatReplayTime(durationMs), color = Color.White)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                positionMs = (videoView.currentPosition - SKIP_MS).coerceAtLeast(0)
                videoView.seekTo(positionMs)
            }, enabled = prepared && !error) { Text("快退 10 秒") }
            Button(onClick = {
                if (videoView.isPlaying) {
                    videoView.pause()
                    playing = false
                } else {
                    if (durationMs > 0 && positionMs >= durationMs - 500) {
                        videoView.seekTo(0)
                        positionMs = 0
                    }
                    videoView.start()
                    playing = true
                }
            }, enabled = prepared && !error,
                colors = ButtonDefaults.buttonColors(containerColor = ControlOrange)) {
                Text(if (playing) "暂停" else "播放")
            }
            Button(onClick = {
                positionMs = (videoView.currentPosition + SKIP_MS).coerceAtMost(durationMs)
                videoView.seekTo(positionMs)
            }, enabled = prepared && !error) { Text("快进 10 秒") }
        }
    }
}

private fun formatReplayTime(ms: Int): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
