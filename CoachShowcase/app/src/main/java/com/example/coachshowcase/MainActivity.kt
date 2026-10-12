package com.example.coachshowcase

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.io.FileNotFoundException

private const val SITE_ORIGIN = "https://appassets.androidplatform.net"
private const val SITE_PREFIX = "/assets/"
private const val START_URL = "$SITE_ORIGIN${SITE_PREFIX}index.html?tenantId=5197"

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val pinStore by lazy { AdminPinStore(this) }
    private val kioskPreferences by lazy {
        getSharedPreferences("kiosk_admin", MODE_PRIVATE)
    }
    private val devicePolicy by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val adminComponent by lazy { ComponentName(this, KioskAdminReceiver::class.java) }
    private val homeAlias by lazy {
        ComponentName(packageName, "com.example.coachshowcase.KioskHomeAlias")
    }
    private var adminTapCount = 0
    private var firstAdminTapAt = 0L
    private var leavingKiosk = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resumeKioskFromLauncher(intent)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.startsWith("$SITE_ORIGIN$SITE_PREFIX") != true) return
                    val script = assets.open("interaction-overrides.js")
                        .bufferedReader().use { it.readText() }
                    view.evaluateJavascript(script, null)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url
                    if (url.scheme != "https" || url.host != "appassets.androidplatform.net" ||
                        !url.path.orEmpty().startsWith(SITE_PREFIX)
                    ) return null

                    val relativePath = url.path.orEmpty().removePrefix(SITE_PREFIX)
                    if (relativePath.isBlank() || relativePath.split('/').any { it == ".." }) {
                        return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
                    }
                    return try {
                        val mime = when (relativePath.substringAfterLast('.', "").lowercase()) {
                            "html" -> "text/html"
                            "js" -> "text/javascript"
                            "css" -> "text/css"
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "webp" -> "image/webp"
                            "gif" -> "image/gif"
                            "svg" -> "image/svg+xml"
                            "mp4" -> "video/mp4"
                            "mp3" -> "audio/mpeg"
                            else -> "application/octet-stream"
                        }
                        WebResourceResponse(mime, null, assets.open("site/$relativePath"))
                    } catch (_: FileNotFoundException) {
                        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
                    }
                }
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
        val root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val adminHotspot = View(this).apply { setOnClickListener { onAdminHotspotTap() } }
        val hotspotSize = (72 * resources.displayMetrics.density).toInt()
        root.addView(adminHotspot, FrameLayout.LayoutParams(
            hotspotSize, hotspotSize, Gravity.TOP or Gravity.START
        ))
        setContentView(root)
        // A restored WebView can retain history without repainting after a device reboot.
        // The store display should always start on its showcase page in a new Activity.
        webView.loadUrl(START_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else webView.loadUrl(START_URL)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        enterKioskIfReady()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resumeKioskFromLauncher(intent)
        enterKioskIfReady()
    }

    private fun resumeKioskFromLauncher(intent: Intent?) {
        if (intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true) {
            kioskPreferences.edit().remove("kiosk_paused").apply()
            leavingKiosk = false
        }
    }

    private fun onAdminHotspotTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - firstAdminTapAt > 4_000L) {
            firstAdminTapAt = now
            adminTapCount = 0
        }
        adminTapCount++
        if (adminTapCount < 7) return
        adminTapCount = 0
        if (pinStore.isConfigured) showAdminExitDialog() else showPinSetupDialog()
    }

    private fun pinInput(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(12))
        isSingleLine = true
    }

    private fun showPinSetupDialog() {
        val pin = pinInput("6–12 位数字密码")
        val confirmation = pinInput("再输入一次")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(pin)
            addView(confirmation)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("设置管理员退出密码")
            .setMessage("只有输入此密码才能退出门店展示。请妥善保存。")
            .setView(fields)
            .setNegativeButton("稍后设置", null)
            .setPositiveButton("保存密码", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val first = pin.text.toString()
                val second = confirmation.text.toString()
                when {
                    first.length !in 6..12 || !first.all(Char::isDigit) ->
                        pin.error = "请输入 6–12 位数字"
                    first != second -> confirmation.error = "两次密码不一致"
                    else -> {
                        pinStore.save(first)
                        dialog.dismiss()
                        Toast.makeText(this, "管理员密码已设置", Toast.LENGTH_LONG).show()
                        enterKioskIfReady()
                    }
                }
            }
        }
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun showAdminExitDialog() {
        val pin = pinInput("管理员密码")
        val padding = (24 * resources.displayMetrics.density).toInt()
        val fields = LinearLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(pin)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("退出门店展示")
            .setView(fields)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认退出", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when (val result = pinStore.verify(pin.text.toString())) {
                    PinResult.Correct -> {
                        dialog.dismiss()
                        if (devicePolicy.isDeviceOwnerApp(packageName)) exitKiosk()
                        else Toast.makeText(this, "密码正确，设备管理权限尚未启用", Toast.LENGTH_LONG).show()
                    }
                    PinResult.NotConfigured -> pin.error = "请先设置密码"
                    is PinResult.Incorrect -> pin.error = "密码错误，还可尝试 ${result.attemptsLeft} 次"
                    is PinResult.Blocked -> pin.error = "请在 ${result.secondsLeft} 秒后重试"
                }
                pin.text.clear()
            }
        }
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun enterKioskIfReady() {
        if (leavingKiosk || kioskPreferences.getBoolean("kiosk_paused", false) ||
            !pinStore.isConfigured ||
            !devicePolicy.isDeviceOwnerApp(packageName) ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked
        ) return
        try {
            devicePolicy.setLockTaskPackages(adminComponent, arrayOf(packageName))
            devicePolicy.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            packageManager.setComponentEnabledSetting(
                homeAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            devicePolicy.addPersistentPreferredActivity(
                adminComponent, homeFilter, homeAlias
            )
            if (devicePolicy.isLockTaskPermitted(packageName) &&
                getSystemService(ActivityManager::class.java).lockTaskModeState !=
                ActivityManager.LOCK_TASK_MODE_LOCKED
            ) startLockTask()
        } catch (error: RuntimeException) {
            Log.e("CoachShowcase", "Unable to enter kiosk mode", error)
            Toast.makeText(this, "门店锁定启动失败，请联系管理员", Toast.LENGTH_LONG).show()
        }
    }

    private fun exitKiosk() {
        leavingKiosk = true
        try {
            kioskPreferences.edit().putBoolean("kiosk_paused", true).commit()
            devicePolicy.clearPackagePersistentPreferredActivities(adminComponent, packageName)
            packageManager.setComponentEnabledSetting(
                homeAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            // Revoking the allowlist exits lock task even when HOME opened this
            // activity through its alias instead of the original launcher task.
            devicePolicy.setLockTaskPackages(adminComponent, emptyArray())
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        } catch (error: RuntimeException) {
            leavingKiosk = false
            kioskPreferences.edit().remove("kiosk_paused").apply()
            enterKioskIfReady()
            Log.e("CoachShowcase", "Unable to leave kiosk mode", error)
            Toast.makeText(this, "退出失败，请联系管理员", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
