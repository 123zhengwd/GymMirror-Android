package com.example.training

import android.graphics.PointF
import android.graphics.Matrix
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.acos
import kotlin.math.hypot
import java.util.concurrent.atomic.AtomicBoolean

data class PoseSnapshot(
    val points: Map<Int, PointF> = emptyMap(),
    val elbowAngle: Int? = null,
    val repetitions: Int = 0,
    val phase: String = "等待人体进入画面",
    val width: Int = 1,
    val height: Int = 1,
    val angularSpeed: Int? = null,
    val repSeconds: Float? = null,
    val averageSeconds: Float? = null,
    val speedLossPercent: Int? = null,
    val averageRange: Int? = null,
    val repetitionData: List<RepMetric> = emptyList(),
    val motion: MotionState = MotionState()
    ,val mediaPoints: Map<Int, PointF> = emptyMap()
    ,val depthPreview: Bitmap? = null
    ,val depthInferenceMs: Long = 0L
    ,val poseFrame: Bitmap? = null
)

data class RepMetric(val index: Int, val seconds: Float, val rangeDegrees: Int, val meanAngularSpeed: Float)

class PoseTracker(context: android.content.Context, private val publish: (PoseSnapshot) -> Unit) : ImageAnalysis.Analyzer {
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )
    private val busy = AtomicBoolean(false)
    @Volatile private var displayMode = 1
    private var lastAnalyzedAt = 0L
    fun setDisplayMode(mode: Int) { displayMode = mode }
    private val depthPose = runCatching { DepthPoseEngine(context.applicationContext) }.onFailure { android.util.Log.e("LITTA_DEPTH", "Engine initialization failed", it) }.getOrNull()
    private var reps = 0
    private var bent = false
    private var stableFrames = 0
    private var lastState = false
    private var lastAngle: Double? = null
    private var lastTime = 0L
    private var cycleStart = 0L
    private var lowestAngle = 180.0
    private val durations = mutableListOf<Float>()
    private val ranges = mutableListOf<Double>()
    private val metrics = mutableListOf<RepMetric>()
    private var angularSpeed: Int? = null
    private val motionTracker = MotionTracker()

    fun setExercise(exercise: MotionExercise) = motionTracker.setExercise(exercise)

    fun reset() { reps = 0; bent = false; stableFrames = 0; lastState = false; lastAngle = null; lastTime = 0L; cycleStart = 0L; lowestAngle = 180.0; durations.clear(); ranges.clear(); metrics.clear(); angularSpeed = null; motionTracker.reset() }

    override fun analyze(proxy: ImageProxy) {
        // Bound expensive inference to ~8 FPS, allowing CameraX preview to run freely.
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastAnalyzedAt < 83L) { proxy.close(); return }
        val image = proxy.image
        if (image == null || !busy.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        lastAnalyzedAt = nowMs
        val rotation = proxy.imageInfo.rotationDegrees
        val w = if (rotation == 90 || rotation == 270) proxy.height else proxy.width
        val h = if (rotation == 90 || rotation == 270) proxy.width else proxy.height
        var mediaPoints = emptyMap<Int, PointF>()
        var depthPreview: Bitmap? = null
        var poseFrame: Bitmap? = null
        runCatching {
            val original = proxy.toBitmap()
            // The displayed frame and detector landmarks must refer to the
            // same pixels. PreviewView can use a different camera crop.
            poseFrame = if (rotation == 0) original else Bitmap.createBitmap(original, 0, 0, original.width, original.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }.onFailure { android.util.Log.e("LITTA_POSE", "Frame conversion failed", it) }
        if (depthPose != null && displayMode != 0 && poseFrame != null) {
            runCatching {
                val upright = poseFrame!!
                // CameraX handles live preview; only inference needs resizing.
                val maxEdge = 480
                val reduction = minOf(1f, maxEdge.toFloat() / maxOf(upright.width, upright.height))
                val working = if (reduction < 1f) Bitmap.createScaledBitmap(upright,
                    (upright.width * reduction).toInt().coerceAtLeast(1),
                    (upright.height * reduction).toInt().coerceAtLeast(1), true) else upright
                mediaPoints = depthPose?.inferPose(working) ?: emptyMap()
                if (displayMode == 2) depthPreview = depthPose?.inferDepthThrottled(working)
            }.onFailure { android.util.Log.e("LITTA_DEPTH", "Inference failed", it) }
        }
        // MediaPipe already tracks the body in these modes. Avoid running a
        // second, independent ML Kit pose detector on the same CameraX frame.
        if (displayMode != 0 && depthPose != null) {
            publish(PoseSnapshot(repetitions = reps, width = w, height = h,
                mediaPoints = mediaPoints, depthPreview = depthPreview, depthInferenceMs = depthPose.lastDepthMs,
                poseFrame = poseFrame))
            busy.set(false)
            proxy.close()
            return
        }
        detector.process(InputImage.fromMediaImage(image, rotation))
            .addOnSuccessListener { pose ->
                val points = pose.allPoseLandmarks
                    .filter { it.inFrameLikelihood > 0.55f }
                    .associate { it.landmarkType to PointF(it.position.x / w, it.position.y / h) }
                val angles = listOf(
                    listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
                    listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
                ).mapNotNull { (a, b, c) ->
                    if (points.containsKey(a) && points.containsKey(b) && points.containsKey(c))
                        angle(points.getValue(a), points.getValue(b), points.getValue(c)) else null
                }
                val value = angles.takeIf { it.isNotEmpty() }?.average()?.toInt()
                val now = android.os.SystemClock.elapsedRealtimeNanos()
                val motion = motionTracker.update(points, now)
                if (value != null) {
                    val dt = (now - lastTime) / 1e9
                    if (lastAngle != null && dt in 0.025..0.5) {
                        val raw = kotlin.math.abs(value - lastAngle!!) / dt
                        angularSpeed = ((angularSpeed ?: raw.toInt()) * 0.65 + raw * 0.35).toInt()
                    }
                    lastAngle = value.toDouble()
                    lastTime = now
                    lowestAngle = minOf(lowestAngle, value.toDouble())
                    val candidate = when {
                        value < 105 -> true
                        value > 150 -> false
                        else -> bent
                    }
                    stableFrames = if (candidate == lastState) stableFrames + 1 else 1
                    lastState = candidate
                    if (stableFrames >= 3 && candidate != bent) {
                        if (!bent && candidate) { cycleStart = now; lowestAngle = value.toDouble() }
                        if (bent && !candidate && cycleStart > 0L) {
                            val duration = (now - cycleStart) / 1e9f
                            val range = 160 - lowestAngle
                            if (duration in 0.35f..12f && range >= 30) {
                                reps++
                                durations.add(duration)
                                ranges.add(range)
                                metrics.add(RepMetric(reps, duration, range.toInt(), (range / duration).toFloat()))
                            }
                            cycleStart = 0L
                        }
                        bent = candidate
                    }
                }
                val avg = durations.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                val loss = if (durations.size >= 2) {
                    // Relative speed proxy: assume comparable range, so speed ~ 1 / duration.
                    val reference = durations.take(3).average()
                    val recent = durations.takeLast(3).average()
                    if (recent > 0) ((1.0 - reference / recent) * 100).toInt().coerceIn(-99, 99) else null
                } else null
                publish(PoseSnapshot(points, value, reps,
                    when { value == null -> "请让肩、肘、手腕进入画面"; bent -> "还原阶段"; else -> "推起阶段" }, w, h,
                    angularSpeed, durations.lastOrNull(), avg, loss, ranges.takeIf { it.isNotEmpty() }?.average()?.toInt(), metrics.toList(), motion,
                    mediaPoints, depthPreview, depthPose?.lastDepthMs ?: 0L, poseFrame))
            }
            .addOnFailureListener { publish(PoseSnapshot(repetitions = reps, phase = "姿态识别失败")) }
            .addOnCompleteListener { busy.set(false); proxy.close() }
    }

    fun close() { detector.close(); depthPose?.close() }

    private fun angle(a: PointF, b: PointF, c: PointF): Double {
        val ax = (a.x - b.x).toDouble()
        val ay = (a.y - b.y).toDouble()
        val cx = (c.x - b.x).toDouble()
        val cy = (c.y - b.y).toDouble()
        val denom = hypot(ax, ay) * hypot(cx, cy)
        if (denom < 1e-8) return 0.0
        return Math.toDegrees(acos(((ax * cx + ay * cy) / denom).coerceIn(-1.0, 1.0)))
    }
}
