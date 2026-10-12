package com.example.training

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.nio.ByteBuffer

/** Inference only; estimated relative depth is not a measured distance. */
class DepthPoseEngine(context: Context) : AutoCloseable {
    private val poseLandmarker = PoseLandmarker.createFromOptions(context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_full.task").build())
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.VIDEO)
            .setNumPoses(1).build())
    private val env = OrtEnvironment.getEnvironment()
    // Most sessions only need skeletons. Load the large depth model only when
    // the user explicitly selects the depth preview.
    private val depthSessionDelegate = lazy { env.createSession(context.assets.open("fused_model_uint8_256.onnx").use { it.readBytes() }) }
    private val depthSession: OrtSession by depthSessionDelegate
    private val inputName by lazy { depthSession.inputNames.first() }
    private var lastDepthAt = 0L
    private var cachedDepth: Bitmap? = null
    var lastDepthMs: Long = 0; private set
    private var previousTimestamp = 0L
    fun inferPose(image: Bitmap): Map<Int, PointF> {
        val timestamp = maxOf(android.os.SystemClock.uptimeMillis(), previousTimestamp + 1)
        previousTimestamp = timestamp
        val result = poseLandmarker.detectForVideo(BitmapImageBuilder(image).build(), timestamp)
        return result.landmarks().firstOrNull()?.mapIndexedNotNull { index, p ->
            if (p.visibility().orElse(1f) < 0.45f) null else index to PointF(p.x(), p.y())
        }?.toMap() ?: emptyMap()
    }
    fun inferDepthThrottled(image: Bitmap): Bitmap? {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastDepthAt < 1800L) return cachedDepth
        lastDepthAt = now
        val scaled = Bitmap.createScaledBitmap(image, 256, 256, true)
        val pixels = IntArray(256 * 256)
        scaled.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        val bytes = ByteBuffer.allocateDirect(256 * 256 * 3)
        for (p in pixels) { bytes.put(Color.red(p).toByte()); bytes.put(Color.blue(p).toByte()); bytes.put(Color.green(p).toByte()) }
        bytes.rewind()
        val start = android.os.SystemClock.elapsedRealtime()
        OnnxTensor.createTensor(env, bytes, longArrayOf(1,256,256,3), OnnxJavaType.UINT8).use { input ->
            depthSession.run(mapOf(inputName to input)).use { out ->
                val byteBuffer = (out[0] as OnnxTensor).byteBuffer
                val values = ByteArray(252 * 252)
                byteBuffer.get(values)
                val palette = IntArray(values.size) { i ->
                    val v = values[i].toInt() and 255
                    Color.rgb(v, (v * 0.75).toInt(), 255 - v)
                }
                val bmp = Bitmap.createBitmap(252, 252, Bitmap.Config.ARGB_8888)
                bmp.setPixels(palette,0,252,0,0,252,252)
                cachedDepth = bmp
            }
        }
        lastDepthMs = android.os.SystemClock.elapsedRealtime() - start
        return cachedDepth
    }
    override fun close() { poseLandmarker.close(); if (depthSessionDelegate.isInitialized()) depthSession.close() }
}
