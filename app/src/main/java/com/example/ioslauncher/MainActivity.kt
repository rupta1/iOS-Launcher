package com.example.ioslauncher

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.app.WallpaperManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.spring
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


class LauncherWelcomeActivity : ComponentActivity() {
    private lateinit var homeRoleRequest: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var permissionsRequest: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private var waitingForHomeSettings = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= 29) {
                val manager = getSystemService(android.app.role.RoleManager::class.java)
                if (manager?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) == true) finishAndRemoveTask()
            }
        }
        permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        setContent { MaterialTheme { LauncherWelcomeScreen(onStart = ::requestHomeRole) } }

        // Ask for the launcher's declared runtime permissions on the welcome screen,
        // before the user selects Start Launcher. Denials remain non-blocking.
        val required = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.distinct().filter {
            ContextCompat.checkSelfPermission(this@LauncherWelcomeActivity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (required.isNotEmpty()) permissionsRequest.launch(required.toTypedArray())
    }
    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val manager = getSystemService(android.app.role.RoleManager::class.java)
            if (manager != null && manager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME))
                homeRoleRequest.launch(manager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
            else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } else {
            waitingForHomeSettings = true
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
    override fun onResume() {
        super.onResume()
        if (waitingForHomeSettings) { waitingForHomeSettings = false; finishAndRemoveTask() }
    }
}

@Composable
private fun LauncherWelcomeScreen(onStart: () -> Unit) {
    val background = Brush.linearGradient(listOf(Color(0xFF101827), Color(0xFF273B65), Color(0xFF111827)))
    Box(Modifier.fillMaxSize().background(background).padding(28.dp),contentAlignment=Alignment.Center) {
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Box(Modifier.size(112.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF9C78F5),Color(0xFF526DE8)))),contentAlignment=Alignment.Center) {
                Icon(Icons.Default.Layers,null,tint=Color.White,modifier=Modifier.size(58.dp))
            }
            Spacer(Modifier.height(34.dp))
            Text("A fresh home.",color=Color.White,fontSize=32.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("A clean, fluid home screen inspired by the simplicity of iOS.",color=Color.White.copy(alpha=.72f),
                fontSize=15.sp,textAlign=TextAlign.Center,lineHeight=23.sp)
            Spacer(Modifier.height(42.dp))
            Button(onClick=onStart,modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(20.dp),
                colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF8B6AE8),contentColor=Color.White)) {
                Text("Start Launcher",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.ArrowForward,null)
            }
            Spacer(Modifier.height(18.dp))
            Text("Choose it as your default Home app",color=Color.White.copy(alpha=.55f),fontSize=12.sp)
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var db: LauncherDb
    lateinit var widgetHost: AppWidgetHost
        private set
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    var wallpaperRevision by mutableIntStateOf(0)
        private set
    var appRefreshRevision by mutableIntStateOf(0)
        private set
    var homeUnlockRevision by mutableIntStateOf(0)
        private set
    private var wasDeviceLocked = false

    override fun onResume() {
        super.onResume()
        appRefreshRevision++
        // Pick up wallpaper changes made in Android's system wallpaper picker.
        wallpaperRevision++
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        val completedUnlockPending = wasDeviceLocked
        wasDeviceLocked = false
        // Keyguard can report unlocked while its exit animation is still running.
        // Wait until that transition has settled before starting the home-entry motion.
        if (completedUnlockPending) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed && hasWindowFocus() &&
                    getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked != true
                ) {
                    homeUnlockRevision++
                }
            }, 650L)
        }
    }
    private var pendingWidgetPage: Int = 0
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val widgetPickRequest = 9101
    private val widgetConfigureRequest = 9102
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            // Keep the selected image as the launcher's own background even on devices
            // that restrict writing the system wallpaper from a third-party launcher.
            db.put("custom_wallpaper_uri", uri.toString())
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                    if (bitmap != null) WallpaperManager.getInstance(this).setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                }
            } catch (_: Exception) { }
            wallpaperRevision++
        }
    }
    fun chooseWallpaper() { wallpaperPicker.launch(arrayOf("image/*")) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LauncherDb(this)
        // Let Android draw the device's live wallpaper behind the launcher window.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        widgetHost = AppWidgetHost(this, 0x10A1)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        applyPreferredRefreshRate()
        // Runtime permissions are requested on the welcome screen before Start Launcher.
        setContent { LauncherRoot(db) }
    }

    private fun applyPreferredRefreshRate() {
        if (db.get("high_refresh_rate", "true") == "true") requestHighestRefreshRate()
    }
    fun availableHomeWidgets(): List<android.appwidget.AppWidgetProviderInfo> =
        widgetManager.installedProviders.sortedWith(compareBy({ it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }, { it.provider.packageName }))

    fun addHomeWidget(page: Int, provider: android.appwidget.AppWidgetProviderInfo) {
        pendingWidgetPage = page.coerceAtLeast(0)
        pendingWidgetId = widgetHost.allocateAppWidgetId()
        val bound = try { widgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId, provider.provider) } catch (_: Exception) { false }
        if (bound) finishWidgetBinding(provider) else {
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            try { startActivityForResult(bind, widgetBindRequest) }
            catch (_: Exception) { widgetHost.deleteAppWidgetId(pendingWidgetId); pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID }
        }
    }

    private val widgetBindRequest = 9103

    private fun finishWidgetBinding(provider: android.appwidget.AppWidgetProviderInfo) {
        if (provider.configure != null) {
            val configure = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = provider.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            }
            try { startActivityForResult(configure, widgetConfigureRequest) }
            catch (_: Exception) { persistPickedWidget() }
        } else persistPickedWidget()
    }

    @Deprecated("Deprecated in Android, retained for widget configuration compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != widgetBindRequest && requestCode != widgetConfigureRequest) return
        if (resultCode != Activity.RESULT_OK) {
            if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            return
        }
        if (requestCode == widgetBindRequest) {
            val provider = widgetManager.getAppWidgetInfo(pendingWidgetId)
            if (provider != null) finishWidgetBinding(provider) else persistPickedWidget()
        } else persistPickedWidget()
    }

    private fun persistPickedWidget() {
        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val current = db.get("home_widgets").split(",").filter { it.isNotBlank() }.toMutableList()
        current.add(pendingWidgetId.toString() + ":" + pendingWidgetPage)
        db.put("home_widgets", current.joinToString(","))
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun onStart() { super.onStart(); try { widgetHost.startListening() } catch (_: Exception) { } }
    override fun onStop() {
        wasDeviceLocked = getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        try { widgetHost.stopListening() } catch (_: Exception) { }
        super.onStop()
    }

    fun removeHomeWidget(id: Int) {
        val remaining = db.get("home_widgets").split(",").filter { it.isNotBlank() && it.substringBefore(":").toIntOrNull() != id }
        db.put("home_widgets", remaining.joinToString(","))
        try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) { }
    }

    fun requestHighestRefreshRate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val mode = windowManager.defaultDisplay.supportedModes.maxByOrNull { it.refreshRate }
            if (mode != null) window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        }
    }
    fun clearPreferredRefreshRate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
        }
    }
    @Suppress("DEPRECATION")
    fun loadApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == packageName) return@mapNotNull null
                InstalledApp(ri.loadLabel(packageManager).toString(), ai.packageName, ri.loadIcon(packageManager))
            }
            .distinctBy { it.packageName }
            .distinctBy { it.label.trim().lowercase(Locale.getDefault()) }
            .filterNot { it.label.equals("Settings", ignoreCase = true) }
            .plus(
                listOf(
                    InstalledApp("Safari", "internal:browser", android.graphics.drawable.GradientDrawable().apply { setColor(android.graphics.Color.rgb(40, 135, 225)); cornerRadius=28f }),
                    InstalledApp("Notes", "internal:notes", android.graphics.drawable.GradientDrawable().apply { setColor(android.graphics.Color.rgb(255, 205, 65)); cornerRadius=28f }),
                    InstalledApp("Settings", "internal:settings", android.graphics.drawable.GradientDrawable().apply { setColor(android.graphics.Color.rgb(125, 130, 145)); cornerRadius=28f }),
                    InstalledApp("Phone", "internal:phone", android.graphics.drawable.GradientDrawable().apply { setColor(android.graphics.Color.rgb(40, 190, 105)); cornerRadius=28f })
                )
            )
            .sortedWith(compareBy<InstalledApp> {
                val preferred = listOf("Phone", "Messages", "Safari", "Chrome", "Calendar", "Photos", "Camera", "Weather", "Clock", "Settings", "Notes", "Maps", "Music", "Files", "Health")
                val position = preferred.indexOfFirst { token -> it.label.contains(token, true) }
                if (position < 0) 1000 else position
            }.thenBy { it.label.lowercase(Locale.getDefault()) })
    }

    fun uninstall(app: InstalledApp) {
        if (app.packageName.startsWith("internal:") || app.packageName.isBlank()) return
        val packageUri=android.net.Uri.fromParts("package",app.packageName,null)
        val uninstallIntent=Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data=packageUri
            putExtra(Intent.EXTRA_RETURN_RESULT,true)
        }
        try {
            startActivity(uninstallIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_DELETE,packageUri))
            } catch (error: Exception) {
                android.widget.Toast.makeText(this,"Unable to open Android's uninstall screen: ${error.localizedMessage ?: "Unknown error"}",android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            android.widget.Toast.makeText(this,"Unable to open Android's uninstall screen: ${error.localizedMessage ?: "Unknown error"}",android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun launch(app: InstalledApp) {
        try {
            when (app.packageName) {
                "internal:browser" -> startActivity(Intent(this, BrowserActivity::class.java))
                "internal:notes" -> startActivity(Intent(this, NotesActivity::class.java))
                "internal:settings" -> startActivity(Intent(Settings.ACTION_SETTINGS))
                "internal:phone" -> startActivity(Intent(this, PhoneActivity::class.java))
                else -> {
                    val i = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
                    db.log("app_launch", app.packageName)
                    startActivity(i)
                }
            }
        } catch (_: Exception) { }
    }
}
