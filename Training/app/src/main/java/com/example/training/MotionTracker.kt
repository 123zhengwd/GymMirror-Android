package com.example.training

import android.graphics.PointF
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.hypot

/** Camera-space measurements, normalized by visible torso length; NOT calibrated VBT m/s. */
enum class MotionExercise(val label: String, val pointLabel: String) {
    PRESS("推胸/卧推", "双手腕"),
    PULL("划船/下拉", "双手腕"),
    CURL("弯举", "双手腕"),
    SQUAT("深蹲", "双髋"),
    DEADLIFT("硬拉", "双肩")
}

data class MotionRep(val index: Int, val meanSpeed: Float, val peakSpeed: Float, val displacement: Float, val seconds: Float)

data class MotionState(
    val pointLabel: String = "",
    val relativePosition: Float? = null,
    val relativeSpeed: Float? = null,
    val trajectory: List<PointF> = emptyList(),
    val repData: List<MotionRep> = emptyList(),
    val velocityLossPercent: Float? = null,
    val status: String = "等待关键点"
)

/** Tracks a selected skeletal landmark and segments cycles by smoothed vertical motion. */
class MotionTracker {
    private var exercise = MotionExercise.PRESS
    private var baseline: PointF? = null
    private var filteredY: Float? = null
    private var previousY: Float? = null
    private var previousTimeNs = 0L
    private var direction = 0
    private var directionFrames = 0
    private var candidateDirection = 0
    private var riseStartNs = 0L
    private var riseStartY = 0f
    private var totalRise = 0f
    private var peakSpeed = 0f
    private var samples = 0
    private var speedIntegral = 0f
    private val trace = ArrayDeque<PointF>()
    private val reps = mutableListOf<MotionRep>()

    fun setExercise(value: MotionExercise) {
        if (exercise != value) { exercise = value; reset() }
    }

    fun reset() {
        baseline = null; filteredY = null; previousY = null; previousTimeNs = 0L
        direction = 0; directionFrames = 0; candidateDirection = 0
        riseStartNs = 0L; riseStartY = 0f; totalRise = 0f
        peakSpeed = 0f; samples = 0; speedIntegral = 0f
        trace.clear(); reps.clear()
    }

    fun update(points: Map<Int, PointF>, nowNs: Long): MotionState {
        val ids = when (exercise) {
            MotionExercise.PRESS, MotionExercise.PULL, MotionExercise.CURL ->
                listOf(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
            MotionExercise.SQUAT -> listOf(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            MotionExercise.DEADLIFT -> listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        }
        // A missing side must not cause jumps when tracking switches between one and two points.
        val visible = ids.mapNotNull { points[it] }
        val shoulders = listOfNotNull(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val hips = listOfNotNull(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP])
        if (visible.size != 2 || shoulders.size != 2 || hips.size != 2) {
            previousTimeNs = 0L; previousY = null; filteredY = null
            direction = 0; riseStartNs = 0L
            return state(null, null, "请完整露出双侧${exercise.pointLabel}和躯干")
        }
        val shoulder = PointF(shoulders.map { it.x }.average().toFloat(), shoulders.map { it.y }.average().toFloat())
        val hip = PointF(hips.map { it.x }.average().toFloat(), hips.map { it.y }.average().toFloat())
        val torso = hypot(shoulder.x - hip.x, shoulder.y - hip.y)
        if (torso < 0.07f) return state(null, null, "躯干尺度过小，请调整摄像头")
        val center = PointF(visible.map { it.x }.average().toFloat(), visible.map { it.y }.average().toFloat())
        val origin = baseline ?: center.also { baseline = PointF(it.x, it.y) }
        val currentY = (center.y - origin.y) / torso
        val y = filteredY?.let { 0.72f * it + 0.28f * currentY } ?: currentY
        filteredY = y
        trace.addLast(center)
        while (trace.size > 100) trace.removeFirst()

        val dt = (nowNs - previousTimeNs) / 1e9f
        if (previousY == null || dt !in 0.025f..0.3f) {
            previousY = y; previousTimeNs = nowNs
            direction = 0; riseStartNs = 0L
            return state(y, null, "关键点追踪中")
        }
        val vy = (y - previousY!!) / dt
        previousY = y; previousTimeNs = nowNs
        val speed = abs(vy)
        // Positive/negative denotes image direction; both directions are tracked, with only
        // the upward interval provisionally treated as concentric for this first prototype.
        val newDirection = when {
            vy < -0.10f -> -1
            vy > 0.10f -> 1
            else -> direction
        }
        directionFrames = if (newDirection == candidateDirection) directionFrames + 1 else 1
        candidateDirection = newDirection
        if (directionFrames >= 3 && newDirection != direction) {
            if (direction == -1 && riseStartNs != 0L) {
                val seconds = (nowNs - riseStartNs) / 1e9f
                val displacement = abs(y - riseStartY)
                if (seconds in 0.3f..8f && totalRise > 0.20f && displacement > 0.14f && samples >= 3) {
                    reps.add(MotionRep(reps.size + 1, speedIntegral / samples, peakSpeed, totalRise, seconds))
                }
            }
            if (newDirection == -1) {
                riseStartNs = nowNs; riseStartY = y; totalRise = 0f
                peakSpeed = 0f; samples = 0; speedIntegral = 0f
            }
            direction = newDirection
        }
        if (direction == -1 && speed < 6f) {
            totalRise += speed * dt
            peakSpeed = maxOf(peakSpeed, speed)
            speedIntegral += speed; samples++
        }
        return state(y, speed, if (direction == -1) "上行阶段（暂按向心）" else "下行/转换阶段")
    }

    private fun state(y: Float?, speed: Float?, status: String): MotionState {
        val reference = reps.take(3).map { it.meanSpeed }.maxOrNull()
        val latest = reps.lastOrNull()?.meanSpeed
        val loss = if (reference != null && latest != null && reference > 0.01f && reps.size >= 2)
            ((1f - latest / reference) * 100f).coerceIn(-100f, 100f) else null
        return MotionState(exercise.pointLabel, y, speed, trace.toList(), reps.toList(), loss, status)
    }
}
