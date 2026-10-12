package com.example.training

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.graphics.PointF
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.training.ui.theme.TrainingTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.roundToInt

private val Ink = Color(0xFF171717)
private val LittaOrange = Color(0xFFFF6418)
private val TrackingGreen = Color(0xFF55E2BB)
private val SoftWhite = Color(0xFFF0F0F0)
private val FigmaBlack = Color(0xFF333333)
private val DinCondensed = FontFamily(Font(R.font.dincond_medium, FontWeight.Medium))
private const val SHOW_POSE_OVERLAY = true
private const val TIMED_STEP_SECONDS = 45
private fun isTimedStep(index: Int) = index == 0 || index == lessonSteps.lastIndex

private data class LessonStep(
    val title: String,
    val shortTitle: String,
    val targetReps: Int,
    val targetWeight: String,
    val targetRpe: Int,
    val motion: MotionExercise,
    val videoRes: Int? = null,
    val cue: String,
    val consecutiveRounds: Int = 1
)

// Teaching clips are assigned per action. Slots without media stay clearly marked
// rather than replaying the wrong exercise demonstration.
private val lessonSteps = listOf(
    LessonStep("弹力带抗阻", "热身", 12, "轻阻力", 3, MotionExercise.PULL, R.raw.warmup_resistance_band, "保持躯干稳定，控制手臂抬起与还原。"),
    LessonStep("绳索拉力", "拉力", 8, "7.5kg", 7, MotionExercise.PULL, R.raw.cable_pull, "保持躯干稳定，肩胛向后收紧，控制绳索拉近身体后缓慢还原。", consecutiveRounds = 3),
    LessonStep("坐姿器械拉力", "器械拉力", 8, "15kg", 7, MotionExercise.PULL, R.raw.seated_machine_pull, "背部贴稳靠垫，肩胛后收，控制把手拉近身体。", consecutiveRounds = 3),
    LessonStep("上斜杠铃卧推", "上斜卧推", 8, "15kg", 7, MotionExercise.PRESS, R.raw.incline_barbell_press, "肩胛稳定贴紧靠垫，控制杠铃下放后向上推起。", consecutiveRounds = 3),
    LessonStep("器械下拉", "下拉", 8, "20kg", 7, MotionExercise.PULL, R.raw.machine_pulldown, "保持胸部抬起，将把手下拉至上胸附近并缓慢还原。", consecutiveRounds = 3),
    LessonStep("上斜哑铃弯举", "上斜弯举", 8, "4kg/手", 7, MotionExercise.CURL, R.raw.incline_dumbbell_curl, "上臂保持稳定，完成弯举后控制哑铃缓慢下放。", consecutiveRounds = 3),
    LessonStep("杠铃卧推", "卧推", 8, "15kg", 7, MotionExercise.PRESS, R.raw.barbell_bench_press, "肩胛稳定贴紧卧推凳，控制杠铃下放至胸前后推起。", consecutiveRounds = 3),
    LessonStep("拉伸", "拉伸", 1, "自重", 2, MotionExercise.PULL, R.raw.cooldown_stretch, "保持呼吸平稳，在舒适范围内完成拉伸，避免弹震。")
)

private data class CourseOverviewAction(
    val title: String,
    val prescription: String,
    val thumbnailRes: Int
)

private data class CourseOverviewSection(
    val title: String,
    val actions: List<CourseOverviewAction>
)

private val courseOverviewSections = listOf(
    CourseOverviewSection(
        "热身准备",
        listOf(CourseOverviewAction("弹力带抗阻", "1组/12次", R.drawable.course_thumb_warmup))
    ),
    CourseOverviewSection(
        "主训动作",
        listOf(
            CourseOverviewAction("绳索拉力", "3组/8次 · 7.5kg · RPE 7", R.drawable.course_thumb_cable_pull),
            CourseOverviewAction("坐姿器械拉力", "3组/8次 · 15kg · RPE 7", R.drawable.course_thumb_seated_pull),
            CourseOverviewAction("上斜杠铃卧推", "3组/8次 · 15kg · RPE 7", R.drawable.course_thumb_incline_press),
            CourseOverviewAction("器械下拉", "3组/8次 · 20kg · RPE 7", R.drawable.course_thumb_pulldown),
            CourseOverviewAction("上斜哑铃弯举", "3组/8次 · 4kg/手 · RPE 7", R.drawable.course_thumb_curl),
            CourseOverviewAction("杠铃卧推", "3组/8次 · 15kg · RPE 7", R.drawable.course_thumb_bench)
        )
    ),
    CourseOverviewSection(
        "拉伸阶段",
        listOf(CourseOverviewAction("拉伸", "1组/30秒", R.drawable.course_thumb_stretch))
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrainingTheme {
                Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
                    TrainingApp()
                }
            }
        }
    }
}

@Composable
private fun TrainingApp() {
    var showTraining by remember { mutableStateOf(false) }
    BackHandler(enabled = showTraining) { showTraining = false }
    if (showTraining) {
        TrainingMirror(onBack = { showTraining = false })
    } else {
        CourseOverviewScreen(onStartCourse = { showTraining = true })
    }
}

@Composable
private fun CourseOverviewScreen(onStartCourse: () -> Unit) {
    val pageBackground = Color(0xFFF8F8F8)
    Box(modifier = Modifier.fillMaxSize().background(pageBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 38.dp, end = 18.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { MemberSummaryCard() }
            item { EquipmentCard() }
            item { CourseContentCard() }
        }

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onStartCourse,
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 28.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LittaOrange, contentColor = Color.White)
            ) {
                Text("去上课", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MemberSummaryCard() {
    Row(
        modifier = Modifier.fillMaxWidth().height(114.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.White).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.course_avatar),
            contentDescription = "会员头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(80.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Miowu", color = FigmaBlack, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberTag("称谓:媛媛")
                MemberTag("女")
                MemberTag("30岁")
                MemberTag("60kg")
            }
        }
        Text("›", color = Color(0xFF999999), fontSize = 34.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun MemberTag(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF6F6F6))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF666666), fontSize = 16.sp)
    }
}

@Composable
private fun EquipmentCard() {
    Row(
        modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.White).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("所需设备", color = FigmaBlack, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(18.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("弹力带", "绳索器械", "推胸器械", "杠铃", "哑铃")) { equipment ->
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFECD5))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(equipment, color = Color(0xFFFF6600), fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun CourseContentCard() {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
            .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 14.dp)
    ) {
        Text("课程内容", color = FigmaBlack, fontSize = 25.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        courseOverviewSections.forEachIndexed { sectionIndex, section ->
            CourseSectionHeader(section.title, section.actions.size)
            section.actions.forEachIndexed { actionIndex, action ->
                CourseActionRow(action)
                if (actionIndex < section.actions.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE7E7E7)))
                }
            }
            if (sectionIndex < courseOverviewSections.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CourseSectionHeader(title: String, actionCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF6F6F6)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), color = FigmaBlack, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Text("共 $actionCount 个动作", color = Color(0xFF999999), fontSize = 17.sp)
    }
}

@Composable
private fun CourseActionRow(action: CourseOverviewAction) {
    Row(
        modifier = Modifier.fillMaxWidth().height(112.dp).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(action.thumbnailRes),
            contentDescription = action.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(168.dp).height(94.dp).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(action.title, color = FigmaBlack, fontSize = 21.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(action.prescription, color = FigmaBlack, fontSize = 16.sp, maxLines = 1)
        }
        Text("›", color = Color(0xFF999999), fontSize = 34.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun TrainingMirror(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.VIDEO_CAPTURE or CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
        }
    }
    var pose by remember { mutableStateOf(PoseSnapshot()) }
    var exercise by remember { mutableStateOf(MotionExercise.PRESS) }
    var lessonIndex by rememberSaveable { mutableIntStateOf(0) }
    var lessonStarted by rememberSaveable { mutableStateOf(true) }
    var lessonPaused by rememberSaveable { mutableStateOf(false) }
    var lessonSeconds by rememberSaveable { mutableIntStateOf(0) }
    var lessonReps by remember { mutableIntStateOf(0) }
    var remainingStepSeconds by remember { mutableIntStateOf(TIMED_STEP_SECONDS) }
    var countdownRunning by remember { mutableStateOf(false) }
    var lessonRound by rememberSaveable { mutableIntStateOf(0) }
    var lastObservedReps by remember { mutableIntStateOf(0) }
    var completedLessonIndices by remember { mutableStateOf(setOf<Int>()) }
    var finishPrompt by remember { mutableStateOf(false) }
    var photoCountdown by remember { mutableIntStateOf(-1) }
    var finishPhoto by remember { mutableStateOf<File?>(null) }
    var reportVisible by remember { mutableStateOf(false) }
    var finishError by remember { mutableStateOf<String?>(null) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val tracker = remember { PoseTracker(context) { snapshot ->
        activity.runOnUiThread { pose = snapshot }
    } }
    LaunchedEffect(exercise) {
        tracker.setExercise(exercise)
        pose = PoseSnapshot()
    }
    LaunchedEffect(lessonStarted) {
        while (lessonStarted) {
            delay(1000)
            lessonSeconds++
        }
    }
    LaunchedEffect(lessonIndex, countdownRunning) {
        if (countdownRunning && remainingStepSeconds > 0) {
            while (remainingStepSeconds > 0) {
                delay(1000)
                remainingStepSeconds--
            }
            countdownRunning = false
            if (isTimedStep(lessonIndex)) {
                completedLessonIndices = completedLessonIndices + lessonIndex
            }
        }
    }
    LaunchedEffect(completedLessonIndices, lessonIndex) {
        if (lessonIndex == lessonSteps.lastIndex && lessonIndex in completedLessonIndices && !reportVisible) {
            finishPrompt = true
        }
    }
    // A lesson's repetitions follow the selected motion tracker, not the
    // separate elbow-angle counter used for the camera's diagnostic HUD.
    LaunchedEffect(pose.motion.repData.size, lessonStarted, lessonPaused, lessonIndex, lessonRound) {
        val observed = pose.motion.repData.size
        if (!isTimedStep(lessonIndex) && observed >= lastObservedReps && lessonStarted && !lessonPaused) {
            lessonReps = (lessonReps + observed - lastObservedReps)
                .coerceAtMost(lessonSteps[lessonIndex].targetReps)
        }
        lastObservedReps = observed
        val currentStep = lessonSteps[lessonIndex]
        if (!isTimedStep(lessonIndex) && lessonReps >= currentStep.targetReps) {
            if (lessonRound + 1 < currentStep.consecutiveRounds) {
                lessonRound++
                lessonReps = 0
                lastObservedReps = observed
            } else {
                completedLessonIndices = completedLessonIndices + lessonIndex
            }
        }
    }
    val selectLesson: (Int) -> Unit = { index ->
        if (index in lessonSteps.indices && index != lessonIndex) {
            countdownRunning = false
            lessonPaused = false
            lessonIndex = index
            lessonReps = 0
            remainingStepSeconds = TIMED_STEP_SECONDS
            lessonRound = 0
            lastObservedReps = pose.motion.repData.size
            exercise = lessonSteps[index].motion
        }
    }
    val changeExercise: (MotionExercise) -> Unit = { next ->
        val target = lessonSteps.indexOfFirst { it.motion == next }
        if (target >= 0) selectLesson(target)
        exercise = next
    }
    DisposableEffect(controller, tracker) {
        controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        controller.setImageAnalysisAnalyzer(analyzerExecutor, tracker)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            tracker.close()
            analyzerExecutor.shutdown()
        }
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var photoSaving by remember { mutableStateOf(false) }
    var lastPhoto by remember { mutableStateOf<File?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var lastVideo by remember { mutableStateOf<File?>(null) }
    var latestReport by remember { mutableStateOf<File?>(null) }
    var reviewOpen by remember { mutableStateOf(false) }
    var galleryOpen by remember { mutableStateOf(false) }
    var splitRatio by rememberSaveable { mutableStateOf(0.50f) }
    var message by remember { mutableStateOf("就位后，点击开始训练") }

    DisposableEffect(controller, permissionGranted) {
        if (permissionGranted) {
            try {
                controller.bindToLifecycle(activity)
                cameraError = null
            } catch (error: Exception) {
                cameraError = error.localizedMessage ?: "摄像头连接失败"
            }
        }
        onDispose {
            recording?.stop()
            controller.unbind()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            seconds = 0
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }

    val startOrStop: () -> Unit = {
        if (isRecording) {
            saving = true
            message = "正在保存训练视频…"
            try {
                val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                root.mkdirs()
                val report = File(root, "LITTA_VBT_${System.currentTimeMillis()}.json")
                val repsJson = JSONArray()
                pose.repetitionData.forEach { item ->
                    repsJson.put(JSONObject().apply {
                        put("rep", item.index)
                        put("duration_seconds", item.seconds.toDouble())
                        put("range_degrees", item.rangeDegrees)
                        put("mean_angular_speed_degrees_per_second", item.meanAngularSpeed.toDouble())
                    })
                }
                val motionReps = JSONArray()
                pose.motion.repData.forEach { item ->
                    motionReps.put(JSONObject().apply {
                        put("rep", item.index)
                        put("mean_relative_speed_torso_per_second", item.meanSpeed.toDouble())
                        put("peak_relative_speed_torso_per_second", item.peakSpeed.toDouble())
                        put("upward_displacement_torso_units", item.displacement.toDouble())
                        put("upward_duration_seconds", item.seconds.toDouble())
                    })
                }
                report.writeText(JSONObject().apply {
                    put("schema_version", 2)
                    put("exercise", exercise.label)
                    put("method", "ML Kit 2D skeletal landmark relative motion; torso normalized; uncalibrated, not VBT m/s")
                    put("tracking_point", exercise.pointLabel)
                    put("motion_rep_metrics", motionReps)
                    put("motion_relative_velocity_loss_percent", pose.motion.velocityLossPercent?.toDouble() ?: JSONObject.NULL)
                    put("recorded_at_epoch_ms", System.currentTimeMillis())
                    put("repetitions", pose.repetitions)
                    put("average_rep_seconds", pose.averageSeconds?.toDouble() ?: JSONObject.NULL)
                    put("relative_velocity_change_percent", pose.speedLossPercent ?: JSONObject.NULL)
                    put("rep_metrics", repsJson)
                }.toString(2))
                latestReport = report
            } catch (error: Exception) {
                message = "数据保存失败：${error.localizedMessage}"
            }
            recording?.stop()
        } else if (!saving && permissionGranted && cameraError == null) {
            tracker.reset()
            pose = PoseSnapshot()
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val videoFile = File(dir, "LITTA_坐姿推胸_$stamp.mp4")
            try {
                val options = FileOutputOptions.Builder(videoFile).build()
                recording = controller.startRecording(
                    options,
                    AudioConfig.create(false),
                    ContextCompat.getMainExecutor(context)
                ) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            message = "正在记录你的动作"
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            saving = false
                            recording = null
                            if (event.hasError()) {
                                message = "保存失败：${event.cause?.localizedMessage ?: event.error}"
                            } else {
                                lastVideo = videoFile
                                message = "训练已保存，点击回看你的动作"
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                saving = false
                message = "无法开始录像：${error.localizedMessage}"
            }
        }
        Unit
    }
    val takePhoto: () -> Unit = {
        if (permissionGranted && cameraError == null && !saving && !photoSaving && !isRecording) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val photoFile = File(dir, "LITTA_训练拍照_$stamp.jpg")
            photoSaving = true
            try {
                controller.takePicture(
                    ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            photoSaving = false
                            lastPhoto = photoFile
                            message = "照片已保存"
                        }

                        override fun onError(exception: ImageCaptureException) {
                            photoSaving = false
                            message = "拍照失败：${exception.localizedMessage}"
                        }
                    }
                )
            } catch (error: Exception) {
                photoSaving = false
                message = "拍照失败：${error.localizedMessage}"
            }
        }
    }
    LaunchedEffect(photoCountdown) {
        if (photoCountdown > 0) {
            delay(1000)
            photoCountdown--
        } else if (photoCountdown == 0) {
            photoCountdown = -1
            if (!permissionGranted || cameraError != null || isRecording || saving || photoSaving) {
                finishError = "摄像头暂不可拍照，请检查权限或停止录像后重试"
            } else {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                directory.mkdirs()
                val file = File(directory, "LITTA_完课合影_${System.currentTimeMillis()}.jpg")
                photoSaving = true
                controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            photoSaving = false
                            finishPhoto = file
                            finishError = null
                        }
                        override fun onError(exception: ImageCaptureException) {
                            photoSaving = false
                            finishError = "合影失败：${exception.localizedMessage}"
                        }
                    })
            }
        }
    }

    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    if (!(finishPrompt && !reportVisible && (photoCountdown >= 0 || photoSaving))) BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Ink)) {
        val density = LocalDensity.current
        if (landscape) {
            val usableWidth = with(density) { maxWidth.toPx() }
            Row(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                Box(modifier = Modifier.weight(splitRatio).fillMaxHeight().clipToBounds()) {
                    LessonPanel(lessonIndex, lessonReps, lessonSeconds, remainingStepSeconds, countdownRunning, lessonRound,
                        completedLessonIndices, onSelect = selectLesson, onBack = onBack,
                        onStartCountdown = { duration ->
                            remainingStepSeconds = duration
                            countdownRunning = true
                        })
                }
                SplitHandle(
                    landscape = true,
                    modifier = Modifier.width(22.dp).fillMaxHeight().zIndex(2f).pointerInput(usableWidth) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            splitRatio = (splitRatio + delta.x / usableWidth).coerceIn(0.28f, 0.72f)
                        }
                    }
                )
                Box(modifier = Modifier.weight(1f - splitRatio).fillMaxHeight().clipToBounds()) {
                    CameraPanel(controller, pose, exercise, changeExercise, permissionGranted, cameraError, isRecording, saving, photoSaving, seconds, message, lastVideo, lastPhoto, latestReport,
                        onPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onRecord = startOrStop,
                        onPhoto = takePhoto,
                        onReview = { galleryOpen = true },
                        onPreviewModeChange = tracker::setDisplayMode
                    )
                }
            }
        } else {
            val usableHeight = with(density) { maxHeight.toPx() }
            Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp, bottom = 12.dp)) {
                Box(modifier = Modifier.weight(splitRatio).fillMaxWidth().clipToBounds()) {
                    LessonPanel(lessonIndex, lessonReps, lessonSeconds, remainingStepSeconds, countdownRunning, lessonRound,
                        completedLessonIndices, onSelect = selectLesson, onBack = onBack,
                        onStartCountdown = { duration ->
                            remainingStepSeconds = duration
                            countdownRunning = true
                        })
                }
                SplitHandle(
                    landscape = false,
                    modifier = Modifier.fillMaxWidth().height(24.dp).zIndex(2f).pointerInput(usableHeight) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            splitRatio = (splitRatio + delta.y / usableHeight).coerceIn(0.28f, 0.72f)
                        }
                    }
                )
                Box(modifier = Modifier.weight(1f - splitRatio).fillMaxWidth().clipToBounds()) {
                    CameraPanel(controller, pose, exercise, changeExercise, permissionGranted, cameraError, isRecording, saving, photoSaving, seconds, message, lastVideo, lastPhoto, latestReport,
                        onPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onRecord = startOrStop,
                        onPhoto = takePhoto,
                        onReview = { galleryOpen = true },
                        onPreviewModeChange = tracker::setDisplayMode
                    )
                }
            }
        }
    }
    if (galleryOpen) {
        MediaGallery(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), onDismiss = { galleryOpen = false })
    }
    if (reviewOpen && lastVideo?.exists() == true) {
        ReviewDialog(lastVideo!!, onDismiss = { reviewOpen = false })
    }
    if (reportVisible) {
        CompletionReport(finishPhoto, lastVideo, lessonSeconds, completedLessonIndices,
            onClose = { reportVisible = false })
    } else if (finishPrompt) {
        Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
            if (photoCountdown >= 0 || photoSaving) {
                AndroidView(factory = { ctx -> PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller
                } }, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
            }
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("训练完成！", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(if (finishPhoto == null) "和伙伴留下一张完课合影吧" else "合影已保存，今天的训练值得纪念",
                    color = Color.White, fontSize = 18.sp)
                if (photoCountdown >= 0) {
                    Text(if (photoCountdown == 0) "拍摄中…" else "$photoCountdown", color = Color.White,
                        fontSize = 96.sp, fontWeight = FontWeight.Bold)
                } else if (photoSaving) {
                    Text("正在保存照片…", color = Color.White)
                } else {
                    finishPhoto?.let { file ->
                        remember(file) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }?.let { bitmap ->
                            Image(bitmap = bitmap, contentDescription = "本次完课合影",
                                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit)
                        }
                    }
                    Button(onClick = { finishError = null; photoCountdown = 5 },
                        enabled = !isRecording && !saving && permissionGranted && cameraError == null,
                        colors = ButtonDefaults.buttonColors(containerColor = LittaOrange)) {
                        Text(if (finishPhoto == null) "5秒后自动合影" else "再拍一张")
                    }
                    Button(onClick = { reportVisible = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)) {
                        Text("查看完课报告")
                    }
                }
                finishError?.let { Text(it, color = Color(0xFFFF9999), fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun CompletionReport(photo: File?, video: File?, elapsed: Int, completed: Set<Int>, onClose: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF5F7FB)),
        contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LittaOrange).padding(24.dp)) {
                Text("‹ 返回合影", color = Color.White, modifier = Modifier.clickable(onClick = onClose))
                Spacer(Modifier.height(20.dp))
                Text("训练完成", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("本次 ${formatTime(elapsed)}  ·  已完成 ${completed.size}/${lessonSteps.size} 个动作",
                    color = Color.White, fontSize = 17.sp)
            }
        }
        item {
            ReportCard("训练视频") {
                Text(if (video?.exists() == true) "本次训练录像已保存在设备中" else "本次未录制训练视频",
                    color = FigmaBlack)
            }
        }
        item {
            ReportCard("合拍照片") {
                val bitmap = remember(photo) { photo?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() } }
                if (bitmap != null) Image(bitmap = bitmap, contentDescription = "完课合影",
                    modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
                else Text("暂无合影", color = FigmaBlack)
            }
        }
        item {
            ReportCard("照片列表") {
                Text(if (photo?.exists() == true) "本次完课合影 1 张" else "暂无照片", color = FigmaBlack)
            }
        }
        item {
            ReportCard("动作记录") {
                lessonSteps.forEachIndexed { index, step ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(step.title, color = FigmaBlack)
                        Text(if (index in completed) "已完成" else "未完成", color = if (index in completed) LittaOrange else Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("▏$title", color = FigmaBlack, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun LessonPanel(
    currentIndex: Int,
    repetitions: Int,
    elapsedSeconds: Int,
    remainingStepSeconds: Int,
    countdownRunning: Boolean,
    currentRound: Int,
    completed: Set<Int>,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onStartCountdown: (Int) -> Unit
) {
    val step = lessonSteps[currentIndex]
    val context = LocalContext.current
    var showCountdownSettings by remember { mutableStateOf(false) }
    var minutes by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(TIMED_STEP_SECONDS) }
    var buttonX by rememberSaveable { mutableStateOf(0.91f) }
    var buttonY by rememberSaveable { mutableStateOf(0.91f) }
    var timerX by remember { mutableStateOf(buttonX) }
    var timerY by remember { mutableStateOf(buttonY) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var showTimelineThumbnails by remember { mutableStateOf(false) }
    BackHandler(enabled = showTimelineThumbnails) { showTimelineThumbnails = false }
    var savedButtonX by rememberSaveable { mutableStateOf(0.91f) }
    var savedButtonY by rememberSaveable { mutableStateOf(0.91f) }
    LaunchedEffect(currentIndex) {
        showCountdownSettings = false
        minutes = 0
        seconds = TIMED_STEP_SECONDS
    }
    var swipeDistance by remember { mutableStateOf(0f) }
    var swipeFromFloating by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White)
    ) {
        val compact = maxHeight < 355.dp
        val density = LocalDensity.current
        val panelWidth = constraints.maxWidth.toFloat()
        val panelHeight = constraints.maxHeight.toFloat()
        val buttonWidth = with(density) { 104.dp.toPx() }
        val buttonHeight = with(density) { 46.dp.toPx() }
        val timerWidth = with(density) { 240.dp.toPx() }
        val timerHeight = with(density) { 112.dp.toPx() }
        fun floatingOffset(x: Float, y: Float, width: Float, height: Float) = IntOffset(
            (x * panelWidth - width / 2).roundToInt()
                .coerceIn(0, (panelWidth - width).coerceAtLeast(0f).roundToInt()),
            (y * panelHeight - height / 2).roundToInt()
                .coerceIn(0, (panelHeight - height).coerceAtLeast(0f).roundToInt())
        )
        fun moveButton(delta: Offset) {
            if (panelWidth > 0f && panelHeight > 0f) {
                buttonX = (buttonX + delta.x / panelWidth)
                    .coerceIn(buttonWidth / (2 * panelWidth), 1f - buttonWidth / (2 * panelWidth))
                buttonY = (buttonY + delta.y / panelHeight)
                    .coerceIn(buttonHeight / (2 * panelHeight), 1f - buttonHeight / (2 * panelHeight))
            }
        }
        fun moveTimer(delta: Offset) {
            if (panelWidth > 0f && panelHeight > 0f) {
                timerX = (timerX + delta.x / panelWidth)
                    .coerceIn(timerWidth / (2 * panelWidth), 1f - timerWidth / (2 * panelWidth))
                timerY = (timerY + delta.y / panelHeight)
                    .coerceIn(timerHeight / (2 * panelHeight), 1f - timerHeight / (2 * panelHeight))
            }
        }
        if (step.videoRes != null) {
            // The lesson player is keyed to the exercise; switching lessons never displays
            // a teaching clip from the previous exercise.
            androidx.compose.runtime.key(step.videoRes) {
                LoopingVideo(
                    uri = "android.resource://${context.packageName}/${step.videoRes}",
                    mute = true,
                    playing = true
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFFF0F0F0)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(step.title, color = FigmaBlack, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("教学视频待补充", color = FigmaBlack.copy(alpha = 0.65f), fontSize = 13.sp)
            }
        }
        Box(Modifier.fillMaxSize()
            .pointerInput(showTimelineThumbnails, showCountdownSettings) {
                detectTapGestures(onTap = {
                    if (!showCountdownSettings) showTimelineThumbnails = !showTimelineThumbnails
                })
            }
            .pointerInput(currentIndex, showCountdownSettings, countdownRunning, showTimelineThumbnails) {
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        swipeDistance = 0f
                        val width = if (countdownRunning) timerWidth else buttonWidth
                        val height = if (countdownRunning) timerHeight else buttonHeight
                        val origin = if (countdownRunning) {
                            floatingOffset(timerX, timerY, width, height)
                        } else {
                            floatingOffset(buttonX, buttonY, width, height)
                        }
                        swipeFromFloating = start.x >= origin.x && start.x <= origin.x + width &&
                            start.y >= origin.y && start.y <= origin.y + height
                    },
                    onDragEnd = {
                        if (!showTimelineThumbnails && !showCountdownSettings && !swipeFromFloating &&
                            kotlin.math.abs(swipeDistance) > 80.dp.toPx()) {
                            onSelect(currentIndex + if (swipeDistance < 0) 1 else -1)
                        }
                        swipeDistance = 0f
                        swipeFromFloating = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!swipeFromFloating && !showTimelineThumbnails) {
                            swipeDistance += dragAmount
                            change.consume()
                        }
                    }
                )
            })

        Row(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 18.dp, top = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "‹", color = Color.White, fontSize = 32.sp, lineHeight = 30.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 2.dp),
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(1f, 1f), 3f))
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                formatTime(elapsedSeconds),
                color = Color.White,
                fontSize = if (compact) 16.sp else 18.sp,
                lineHeight = if (compact) 19.sp else 21.sp,
                fontFamily = DinCondensed,
                fontWeight = FontWeight.Medium,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.72f), Offset(1.5f, 1.5f), 4f)
                )
            )
            Text(
                step.title,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = if (compact) 23.sp else 26.sp,
                lineHeight = if (compact) 27.sp else 30.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.78f), Offset(1f, 1f), 3f)
                )
            )
            Row(
                modifier = Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(step.consecutiveRounds) { round ->
                    Box(
                        Modifier.size(7.dp).clip(CircleShape).background(
                            when {
                                round == currentRound -> LittaOrange
                                round < currentRound -> Color.White.copy(alpha = 0.9f)
                                else -> Color.White.copy(alpha = 0.34f)
                            }
                        )
                    )
                }
            }
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd).zIndex(5f).padding(top = 10.dp, end = 12.dp)) {
            Box(
                modifier = Modifier.size(46.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                    .clickable { optionsExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Text("⋯", color = Color.White, fontSize = 30.sp, lineHeight = 30.sp)
            }
            DropdownMenu(
                expanded = optionsExpanded,
                onDismissRequest = { optionsExpanded = false },
                modifier = Modifier.background(Color(0xFF252525))
            ) {
                DropdownMenuItem(text = { Text("替换动作", color = Color.White) }, onClick = {
                    optionsExpanded = false
                    showExercisePicker = true
                })
                DropdownMenuItem(text = { Text("保存操作区位置", color = Color.White) }, onClick = {
                    savedButtonX = buttonX
                    savedButtonY = buttonY
                    optionsExpanded = false
                    android.widget.Toast.makeText(context, "已保存操作区位置", android.widget.Toast.LENGTH_SHORT).show()
                })
                DropdownMenuItem(text = { Text("恢复操作区位置", color = Color.White) }, onClick = {
                    buttonX = savedButtonX
                    buttonY = savedButtonY
                    optionsExpanded = false
                    android.widget.Toast.makeText(context, "已恢复操作区位置", android.widget.Toast.LENGTH_SHORT).show()
                })
            }
        }
        if (showExercisePicker) {
            Dialog(onDismissRequest = { showExercisePicker = false }) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF252525), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text("替换动作", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(340.dp)) {
                        items(lessonSteps.size) { index ->
                            Text(
                                lessonSteps[index].title,
                                color = if (index == currentIndex) LittaOrange else Color.White,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showExercisePicker = false
                                    onSelect(index)
                                }.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!showCountdownSettings && !countdownRunning) {
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .zIndex(3f)
                    .offset {
                        val visibleY = if (showTimelineThumbnails) {
                            buttonY.coerceAtMost(((panelHeight - with(density) { 137.dp.toPx() }) / panelHeight).coerceAtLeast(0f))
                        } else buttonY
                        floatingOffset(buttonX, visibleY, buttonWidth, buttonHeight)
                    }
                    .size(104.dp, 46.dp)
                    .pointerInput(panelWidth, panelHeight) {
                        detectDragGesturesAfterLongPress { change, dragAmount ->
                            moveButton(dragAmount)
                            change.consume()
                        }
                    }
                    .clip(RoundedCornerShape(23.dp)).background(LittaOrange)
                    .clickable { showCountdownSettings = true },
                contentAlignment = Alignment.Center
            ) {
                Text("倒计时", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!showCountdownSettings && countdownRunning) {
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .zIndex(3f)
                    .offset {
                        val visibleY = if (showTimelineThumbnails) {
                            timerY.coerceAtMost(((panelHeight - with(density) { 175.dp.toPx() }) / panelHeight).coerceAtLeast(0f))
                        } else timerY
                        floatingOffset(timerX, visibleY, timerWidth, timerHeight)
                    }
                    .size(240.dp, 112.dp)
                    .pointerInput(panelWidth, panelHeight) {
                        detectDragGestures { change, dragAmount ->
                            moveTimer(dragAmount)
                            change.consume()
                        }
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center
            ) {
                Text(formatTime(remainingStepSeconds), color = Color.White,
                    fontSize = if (compact) 64.sp else 88.sp, fontFamily = DinCondensed,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = Shadow(LittaOrange.copy(alpha = 0.8f), Offset.Zero, 14f)))
            }
        }
        if (showTimelineThumbnails) {
            val thumbnails = remember { courseOverviewSections.flatMap { it.actions } }
            val thumbnailListState = androidx.compose.foundation.lazy.rememberLazyListState(
                initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)
            )
            LaunchedEffect(showTimelineThumbnails) {
                thumbnailListState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(4f)
                    .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(top = 10.dp, bottom = 8.dp),
            ) {
                LazyRow(
                    state = thumbnailListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(lessonSteps.size) { index ->
                        val active = index == currentIndex
                        Column(
                            modifier = Modifier.width(130.dp)
                                .clickable {
                                    onSelect(index)
                                    showTimelineThumbnails = false
                                },
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Image(
                                painter = painterResource(thumbnails[index].thumbnailRes),
                                contentDescription = lessonSteps[index].title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(74.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(if (active) 2.dp else 0.dp,
                                        if (active) LittaOrange else Color.Transparent, RoundedCornerShape(8.dp))
                            )
                            Text(lessonSteps[index].title,
                                color = if (active) LittaOrange else Color.White,
                                fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clickable { showTimelineThumbnails = true }
                    .padding(start = 12.dp, end = 12.dp, top = 15.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                lessonSteps.forEachIndexed { index, _ ->
                    Box(
                        Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (index <= currentIndex || index in completed) LittaOrange
                                else Color.White.copy(alpha = 0.55f))
                    )
                }
            }
        }
        if (showCountdownSettings) {
            val buttonOrigin = floatingOffset(buttonX, buttonY, buttonWidth, buttonHeight)
            val editorWidth = if (editorSize.width > 0) editorSize.width else with(density) { 260.dp.roundToPx() }
            val editorHeight = if (editorSize.height > 0) editorSize.height else with(density) { 340.dp.roundToPx() }
            val editorX = if (buttonX < 0.5f) buttonOrigin.x
                else buttonOrigin.x + buttonWidth.roundToInt() - editorWidth
            val editorY = if (buttonY < 0.5f) buttonOrigin.y
                else buttonOrigin.y + buttonHeight.roundToInt() - editorHeight
            Column(
                modifier = Modifier.align(Alignment.TopStart)
                    .zIndex(3f)
                    .offset {
                        IntOffset(
                            editorX.coerceIn(0, (panelWidth.roundToInt() - editorWidth).coerceAtLeast(0)),
                            editorY.coerceIn(0, (panelHeight.roundToInt() - editorHeight).coerceAtLeast(0))
                        )
                    }
                    .onSizeChanged { editorSize = it }
                    .background(Color(0xF2171717), RoundedCornerShape(22.dp))
                    .padding(horizontal = 25.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("设置倒计时", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CountdownUnit("分钟", minutes, { minutes = (minutes + 1).coerceAtMost(99) },
                        { minutes = (minutes - 1).coerceAtLeast(0) })
                    Text(":", color = Color.White, fontSize = 38.sp)
                    CountdownUnit("秒", seconds, { seconds = (seconds + 1) % 60 },
                        { seconds = (seconds + 59) % 60 })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showCountdownSettings = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("取消") }
                    Button(onClick = {
                        timerX = buttonX
                        timerY = buttonY
                        onStartCountdown(minutes * 60 + seconds)
                        showCountdownSettings = false
                    }, enabled = minutes > 0 || seconds > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = LittaOrange)) { Text("确认") }
                }
            }
        }
    }
}

@Composable
private fun CountdownUnit(label: String, value: Int, onIncrease: () -> Unit, onDecrease: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Text("＋", color = LittaOrange, fontSize = 28.sp,
            modifier = Modifier.clickable(onClick = onIncrease).padding(horizontal = 12.dp))
        Text("%02d".format(Locale.US, value), color = Color.White, fontSize = 38.sp,
            fontFamily = DinCondensed)
        Text("－", color = LittaOrange, fontSize = 28.sp,
            modifier = Modifier.clickable(onClick = onDecrease).padding(horizontal = 12.dp))
    }
}

@Composable
private fun CoachDataPanel(
    repetitions: Int,
    weight: String,
    rpe: Int,
    remainingSeconds: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(126.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CoachMetricCard(
            label = if (remainingSeconds != null) "倒计时" else "次数",
            value = if (remainingSeconds != null) formatTime(remainingSeconds) else repetitions.toString(),
            modifier = Modifier.fillMaxWidth()
        )
        CoachMetricCard(
            label = "重量",
            value = weight,
            modifier = Modifier.fillMaxWidth()
        )
        CoachMetricCard(
            label = "RPE",
            value = rpe.toString(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CoachMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, color = LittaOrange, fontSize = 22.sp,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun CameraPanel(
    controller: LifecycleCameraController,
    pose: PoseSnapshot,
    exercise: MotionExercise,
    onExerciseChange: (MotionExercise) -> Unit,
    permissionGranted: Boolean,
    cameraError: String?,
    isRecording: Boolean,
    saving: Boolean,
    photoSaving: Boolean,
    seconds: Int,
    message: String,
    lastVideo: File?,
    lastPhoto: File?,
    latestReport: File?,
    onPermission: () -> Unit,
    onRecord: () -> Unit,
    onPhoto: () -> Unit,
    onReview: () -> Unit,
    onPreviewModeChange: (Int) -> Unit
) {
    var previewMode by remember { mutableIntStateOf(1) } // 0: ML Kit, 1: MediaPipe, 2: depth + MediaPipe
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF171F26))) {
        if (permissionGranted && cameraError == null) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            // Show exactly the frame analyzed by the detector. A separate
            // PreviewView stream can have a different crop on this device.
            pose.poseFrame?.let { frame ->
                Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer { scaleX = -1f }) {
                    Image(frame.asImageBitmap(), contentDescription = "实时摄像头画面",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (SHOW_POSE_OVERLAY) {
                        PoseOverlay(pose, useMediaPipe = previewMode != 0,
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            Column(modifier = Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cameraError ?: "请允许访问摄像头，以实时查看训练动作", color = SoftWhite, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPermission) { Text("打开摄像头权限") }
            }
        }
        Row(modifier = Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("原版", "MediaPipe", "深度+骨骼").forEachIndexed { index, title ->
                Text(title, fontSize = 11.sp, color = if (previewMode == index) Ink else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (previewMode == index) LittaOrange else Ink.copy(alpha = 0.7f))
                        .clickable { previewMode = index; onPreviewModeChange(index) }.padding(horizontal = 9.dp, vertical = 7.dp))
            }
        }
        if (previewMode == 2 && pose.depthPreview != null) {
            Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 50.dp, end = 10.dp)
                .background(Ink.copy(alpha = 0.88f), RoundedCornerShape(10.dp)).padding(5.dp)) {
                Image(bitmap = pose.depthPreview.asImageBitmap(), contentDescription = "Depth Anything V2 预测的相对深度",
                    modifier = Modifier.size(124.dp), contentScale = ContentScale.FillBounds)
                Text("AI 相对深度 · ${pose.depthInferenceMs}ms", fontSize = 9.sp, color = Color.White)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 11.dp)
                .width(240.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecording) {
                Text("● REC ${formatTime(seconds)}", color = Color(0xFFFF7777), fontSize = 11.sp,
                    modifier = Modifier.background(Ink.copy(alpha = 0.72f), RoundedCornerShape(8.dp)).padding(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRecord,
                    enabled = permissionGranted && cameraError == null && !saving && !photoSaving,
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFFED5B60) else LittaOrange, contentColor = Ink),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(if (saving) "保存中" else if (isRecording) "■ 停止录制" else "● 录制视频", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onPhoto,
                    enabled = permissionGranted && cameraError == null && !saving && !photoSaving && !isRecording,
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(if (photoSaving) "拍照中" else "◎ 拍照", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("▦ 照片与视频", fontSize = 12.sp, color = SoftWhite,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Ink.copy(alpha = 0.72f))
                    .clickable(onClick = onReview).padding(horizontal = 12.dp, vertical = 7.dp))
            if (latestReport != null) {
                Text("✓ 已保存", color = LittaOrange, fontSize = 10.sp)
            }
        }

    }
}

@Composable
private fun PoseOverlay(pose: PoseSnapshot, useMediaPipe: Boolean = true, modifier: Modifier = Modifier) {
    val mlBones = listOf(
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
    )
    // MediaPipe Pose Landmarker anatomical connections (33 landmarks).
    val mpBones = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 23, 12 to 24, 23 to 24, 23 to 25, 24 to 26, 25 to 27, 26 to 28)
    Canvas(modifier) {
        // ContentScale.Fit centers the complete analysis frame in this canvas.
        // The parent mirrors both image and skeleton for the front camera.
        val fit = minOf(size.width / pose.width, size.height / pose.height)
        val left = (size.width - pose.width * fit) / 2f
        val top = (size.height - pose.height * fit) / 2f
        fun mapped(point: PointF): Offset? {
            if (pose.width <= 1 || pose.height <= 1) return null
            return Offset(left + point.x * pose.width * fit,
                top + point.y * pose.height * fit)
        }
        val media = useMediaPipe && pose.mediaPoints.isNotEmpty()
        val selected = if (media) pose.mediaPoints else pose.points
        val bones = if (media) mpBones else mlBones
        fun position(id: Int): Offset? = selected[id]?.let { mapped(it) }
        bones.forEach { (a, b) ->
            val start = position(a)
            val end = position(b)
            if (start != null && end != null) drawLine(TrackingGreen, start, end, strokeWidth = 5f)
        }
        selected.keys.forEach { key ->
            position(key)?.let { drawCircle(Color.White, radius = 5f, center = it) }
        }
    }
}

@Composable
private fun SplitHandle(landscape: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Ink), contentAlignment = Alignment.Center) {
        Box(
            modifier = (if (landscape) Modifier.fillMaxHeight().width(1.dp)
                else Modifier.fillMaxWidth().height(1.dp))
                .background(Color.White.copy(alpha = 0.10f))
        )
        Box(
            modifier = (if (landscape) Modifier.width(4.dp).height(36.dp)
                else Modifier.width(36.dp).height(4.dp))
                .clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.38f))
        )
    }
}

@Composable
private fun LoopingVideo(uri: String, mute: Boolean, playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failure by remember(uri) { mutableStateOf<String?>(null) }
    val player = remember(uri, mute) {
        LessonVideoView(context, Uri.parse(uri), mute) { failure = it }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    AndroidView(
        factory = { player },
        update = { it.playbackEnabled = playing },
        modifier = modifier.fillMaxSize()
    )
        if (failure != null) {
            Text(failure.orEmpty(), color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Ink.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(12.dp))
        }
    }
}

@Composable
private fun ReviewDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize(0.92f).background(Ink, RoundedCornerShape(18.dp)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("训练录像回看", color = SoftWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("关闭") }
            }
            ReplayVideoPlayer(file, modifier = Modifier.weight(1f).fillMaxWidth())
            Text("仅在本机保存 · 无音频", color = SoftWhite.copy(alpha = 0.66f), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun MediaGallery(pictures: File?, movies: File?, onDismiss: () -> Unit) {
    val photos = pictures?.listFiles()?.filter { it.isFile && it.extension.equals("jpg", true) }
        ?.sortedByDescending { it.lastModified() }.orEmpty()
    val videos = movies?.listFiles()?.filter { it.isFile && it.extension.equals("mp4", true) }
        ?.sortedByDescending { it.lastModified() }.orEmpty()
    val media = remember(photos, videos) { (photos + videos).sortedByDescending { it.lastModified() } }
    var selected by remember { mutableStateOf<File?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("照片与视频", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Button(onClick = onDismiss) { Text("关闭") }
            }
            if (selected != null) {
                val file = selected!!
                Text("‹ 返回列表", color = LittaOrange, modifier = Modifier.clickable { selected = null })
                if (file.extension.equals("mp4", true)) {
                    ReplayVideoPlayer(file, modifier = Modifier.weight(1f).fillMaxWidth())
                } else {
                    val photo = remember(file) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
                    if (photo != null) Image(photo, contentDescription = "拍摄照片", modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentScale = ContentScale.Fit)
                }
            } else {
                Text("全部 ${media.size} · 照片 ${photos.size} · 视频 ${videos.size}", color = SoftWhite, fontSize = 14.sp)
                if (media.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("还没有照片或视频", color = SoftWhite)
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        gridItems(media, key = { it.absolutePath }) { file ->
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.DarkGray)
                                .clickable { selected = file }) {
                                val preview by produceState<Pair<Bitmap?, String?>>(initialValue = null to null, file.absolutePath, file.lastModified()) {
                                    value = withContext(Dispatchers.IO) {
                                        loadMediaThumbnail(file) to if (file.extension.equals("mp4", true)) videoDuration(file) else null
                                    }
                                }
                                if (preview.first != null) {
                                    Image(preview.first!!.asImageBitmap(), contentDescription = file.name,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                if (file.extension.equals("mp4", true)) {
                                    Text("▶", color = Color.White, fontSize = 20.sp,
                                        modifier = Modifier.align(Alignment.Center))
                                    Text(preview.second.orEmpty(), color = Color.White, fontSize = 11.sp,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                                            .background(Color.Black.copy(alpha = 0.55f)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadMediaThumbnail(file: File): Bitmap? = try {
    if (file.extension.equals("mp4", true)) {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320)
                ?: retriever.getFrameAtTime(0L)?.let { Bitmap.createScaledBitmap(it, 320, 320, true) }
        }
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val sample = (maxOf(options.outWidth, options.outHeight) / 320).coerceAtLeast(1)
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
} catch (_: Exception) { null }

private fun videoDuration(file: File): String = try {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.absolutePath)
        formatTime(((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000).toInt())
    }
} catch (_: Exception) { "视频" }

private fun formatTime(seconds: Int) = "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
