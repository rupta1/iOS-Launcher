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


data class HomeFolder(val id:String, val title:String, val packages:List<String>, val page:Int, val slot:Int)
internal data class HomeGridTile(val key:String, val slot:Int, val spanX:Int=1, val spanY:Int=1)
internal data class HomeGridCell(val column:Int, val row:Int)
private data class DragGridPlacement(val key:String,val slot:Int,val spanX:Int=1,val spanY:Int=1)

private fun calculateHomeGridCells(tiles:List<HomeGridTile>, columns:Int, rows:Int, compact:Boolean):Map<String,HomeGridCell> =
    HomeWorkspaceEngine.layout(tiles, columns, rows, compact)

private fun decodeHomeFolders(raw:String):List<HomeFolder> = try {
    val array=org.json.JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        val item=array.optJSONObject(index) ?: return@mapNotNull null
        val packages=item.optJSONArray("packages") ?: org.json.JSONArray()
        HomeFolder(item.optString("id"),item.optString("title","Folder"),
            (0 until packages.length()).map { packages.optString(it) }.filter { it.isNotBlank() },
            item.optInt("page",0),item.optInt("slot",0))
    }
} catch (_:Exception) { emptyList() }

private fun encodeHomeFolders(folders:List<HomeFolder>):String = org.json.JSONArray().apply {
    folders.forEach { folder ->
        put(org.json.JSONObject().put("id",folder.id).put("title",folder.title)
            .put("packages",org.json.JSONArray(folder.packages)).put("page",folder.page).put("slot",folder.slot))
    }
}.toString()

data class InstalledApp(val label: String, val packageName: String, val icon: Drawable)

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot(db: LauncherDb) {
    val context = LocalContext.current
    val activity = context as MainActivity
    var apps by remember {
        val loaded = activity.loadApps().distinctBy { it.packageName }
        val saved = db.get("app_order").split("|").filter { it.isNotBlank() }
        mutableStateOf(if (saved.isEmpty()) loaded else loaded.sortedBy { app ->
            val index = saved.indexOf(app.packageName)
            if (index < 0) Int.MAX_VALUE else index
        })
    }
    var appPageAssignments by remember { mutableStateOf(db.get("app_pages").split(";").mapNotNull { token ->
        val parts=token.split(":")
        val packageName=parts.getOrNull(0)
        val pageIndex=parts.getOrNull(1)?.toIntOrNull()
        if(!packageName.isNullOrBlank() && pageIndex!=null) packageName to pageIndex else null
    }.toMap()) }
    var appSlotAssignments by remember { mutableStateOf(db.get("app_slots").split(";").mapNotNull { token ->
        val parts=token.split(":")
        val packageName=parts.getOrNull(0)
        val slot=parts.getOrNull(1)?.toIntOrNull()
        if(!packageName.isNullOrBlank() && slot!=null) packageName to slot else null
    }.toMap()) }
    var widgetSlotAssignments by remember { mutableStateOf(db.get("widget_slots").split(";").mapNotNull { token ->
        val parts=token.split(":"); val id=parts.getOrNull(0)?.toIntOrNull(); val slot=parts.getOrNull(1)?.toIntOrNull()
        if(id!=null && slot!=null) id to slot else null
    }.toMap()) }
    var folders by remember { mutableStateOf(decodeHomeFolders(db.get("home_folders"))) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var lastOpenedFolderId by remember { mutableStateOf<String?>(null) }
    var renamingFolderId by remember { mutableStateOf<String?>(null) }
    var renameFolderText by remember { mutableStateOf("") }
    LaunchedEffect(openFolderId) { if(openFolderId!=null) lastOpenedFolderId=openFolderId }
    var dockPackages by remember {
        val saved=db.get("dock_packages").split("|").filter { it.isNotBlank() }.distinct()
        val defaults=listOfNotNull(
            apps.firstOrNull { it.packageName=="internal:phone" },
            apps.firstOrNull { it.label.contains("Camera",true) || it.packageName.contains("camera",true) },
            apps.firstOrNull { it.packageName=="com.android.chrome" || it.label.equals("Chrome",true) },
            apps.firstOrNull { it.packageName=="internal:settings" || it.label.equals("Settings",true) }
        ).distinctBy { it.packageName }.map { it.packageName }
        val previousAutomaticDefaults=apps.filter { app ->
            (app.label.contains("Phone",true) && !app.label.contains("PhonePe",true)) ||
                app.packageName=="com.google.android.dialer" || app.packageName=="com.android.dialer" ||
                app.label.contains("Chrome",true) || app.label.contains("Music",true) || app.label.contains("Message",true)
        }.distinctBy { it.packageName }.take(5).map { it.packageName }
        mutableStateOf((if(saved.isEmpty() || saved==previousAutomaticDefaults) defaults else (listOf("internal:phone") + saved.filterNot { it=="internal:phone" })).take(5))
    }
    var draggingFromDock by remember { mutableStateOf(false) }
    var draggingFromFolder by remember { mutableStateOf(false) }
    var dragSourceFolderId by remember { mutableStateOf<String?>(null) }
    var dragOriginBounds by remember { mutableStateOf(Rect.Zero) }
    var dragOriginPage by remember { mutableIntStateOf(0) }
    var dragOriginSlot by remember { mutableIntStateOf(0) }
    var folderOverlayBounds by remember { mutableStateOf(Rect.Zero) }

    var pendingDragPage by remember { mutableIntStateOf(0) }
    var homeGridBounds by remember { mutableStateOf(Rect.Zero) }
    // AnimatedContent keeps both pages composed during a swipe. Keep each page's
    // measured grid bounds separately so backward drags never calculate a drop
    // against the outgoing page's stale bounds.
    val homeGridBoundsByPage = remember { mutableStateMapOf<Int, Rect>() }
    fun gridBoundsFor(pageIndex:Int):Rect =
        homeGridBoundsByPage[pageIndex]?.takeIf { it.width>0f && it.height>0f } ?: homeGridBounds
    var draggingApp by remember { mutableStateOf<InstalledApp?>(null) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var widgetProviders by remember { mutableStateOf(emptyList<android.appwidget.AppWidgetProviderInfo>()) }
    var homeWidgets by remember { mutableStateOf(db.get("home_widgets").split(",").mapNotNull { token ->
        val parts = token.split(":")
        val id = parts.getOrNull(0)?.toIntOrNull()
        val widgetPage = parts.getOrNull(1)?.toIntOrNull()
        if (id != null && widgetPage != null) id to widgetPage else null
    }) }
    var draggingWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var resizingWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var showWeatherWidget by remember { mutableStateOf(db.get("show_weather_widget", "true") == "true") }
    var showTodayWidget by remember { mutableStateOf(db.get("show_today_widget", "true") == "true") }
    var weatherWidgetPage by remember { mutableIntStateOf(db.get("weather_widget_page", "0").toIntOrNull() ?: 0) }
    var todayWidgetPage by remember { mutableIntStateOf(db.get("today_widget_page", "0").toIntOrNull() ?: 0) }
    var widgetDragPosition by remember { mutableStateOf(Offset.Zero) }
    var widgetCanvasBounds by remember { mutableStateOf(Rect.Zero) }
    var widgetPositions by remember { mutableStateOf(db.get("home_widget_positions").split(";").mapNotNull { item ->
        val parts=item.split(":")
        val id=parts.getOrNull(0)?.toIntOrNull()
        val x=parts.getOrNull(1)?.toFloatOrNull()
        val y=parts.getOrNull(2)?.toFloatOrNull()
        if(id!=null && x!=null && y!=null) id to Offset(x,y) else null
    }.toMap()) }
    var widgetSizes by remember { mutableStateOf(db.get("home_widget_sizes").split(";").mapNotNull { item ->
        val parts=item.split(":")
        val id=parts.getOrNull(0)?.toIntOrNull()
        val w=parts.getOrNull(1)?.toFloatOrNull()
        val h=parts.getOrNull(2)?.toFloatOrNull()
        if(id!=null && w!=null && h!=null) id to (w.dp to h.dp) else null
    }.toMap()) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragEdgeDirection by remember { mutableIntStateOf(0) }
    var dragPreviewSlots by remember { mutableStateOf<Map<String,Int>>(emptyMap()) }

    val appBounds = remember { mutableStateMapOf<String, Rect>() }
    // Bounds survive page transitions; this set identifies the page currently being laid out.
    var currentPageAppKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val widgetBounds = remember { mutableStateMapOf<Int, Rect>() }
    val widgetHandleBounds = remember { mutableStateMapOf<Int, Rect>() }
    val widgetResizeHandleBounds = remember { mutableStateMapOf<Int, Rect>() }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }
    val folderMemberBounds = remember { mutableStateMapOf<String, Rect>() }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    val dockAppBounds = remember { mutableStateMapOf<String, Rect>() }

    val density = LocalDensity.current
    val uninstallDropZoneHeight = with(density) { 112.dp.toPx() }
    var drawer by remember { mutableStateOf(false) }
    var pendingUninstallApp by remember { mutableStateOf<InstalledApp?>(null) }
    var awaitingUninstallPackage by remember { mutableStateOf<String?>(null) }
    var uninstallRequestResumeRevision by remember { mutableIntStateOf(0) }
    var drawerTransparency by remember { mutableFloatStateOf(db.get("drawer_transparency","0.30").toFloatOrNull()?.coerceIn(0f,.80f) ?: .30f) }
    var drawerBlur by remember { mutableFloatStateOf(db.get("drawer_blur","20").toFloatOrNull()?.coerceIn(0f,32f) ?: 20f) }
    LaunchedEffect(drawer,drawerBlur) {
        if (Build.VERSION.SDK_INT >= 31) {
            val window=activity.window
            val params=window.attributes
            if (drawer && drawerBlur > 0f) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                params.blurBehindRadius=with(density) { drawerBlur.dp.roundToPx() }
                params.dimAmount=0f
            } else {
                params.blurBehindRadius=0
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            window.attributes=params
        }
    }
    LaunchedEffect(activity.appRefreshRevision,awaitingUninstallPackage) {
        val packageName=awaitingUninstallPackage ?: return@LaunchedEffect
        if (activity.appRefreshRevision <= uninstallRequestResumeRevision) return@LaunchedEffect
        val stillInstalled=try {
            context.packageManager.getApplicationInfo(packageName,0)
            true
        } catch (_:PackageManager.NameNotFoundException) { false }
        if (!stillInstalled) {
            apps=activity.loadApps()
            val updatedFolders=folders.mapNotNull { folder ->
                val members=folder.packages.filterNot { it==packageName }
                if(members.size>1) folder.copy(packages=members) else null
            }
            folders=updatedFolders
            db.put("home_folders",encodeHomeFolders(updatedFolders))
            dockPackages=dockPackages.filterNot { it==packageName }
            db.put("dock_packages",dockPackages.joinToString("|"))
            appPageAssignments=appPageAssignments-packageName
            appSlotAssignments=appSlotAssignments-packageName
            db.put("app_pages",appPageAssignments.entries.joinToString(";"){it.key+":"+it.value})
            db.put("app_slots",appSlotAssignments.entries.joinToString(";"){it.key+":"+it.value})
        }
        awaitingUninstallPackage=null
    }
    var controlCenter by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var columns by remember { mutableIntStateOf(db.get("grid_columns", "4").toIntOrNull() ?: 4) }
    var labels by remember { mutableStateOf(db.get("show_labels", "true") == "true") }
    var themeMode by remember { mutableStateOf(db.get("theme_mode", "system")) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> systemDark }
    var page by remember { mutableIntStateOf(0) }
    var extraHomePages by remember { mutableIntStateOf(db.get("extra_home_pages","0").toIntOrNull()?.coerceAtLeast(0) ?: 0) }
    var homeEditMode by remember { mutableStateOf(false) }
    var rows by remember { mutableIntStateOf(db.get("grid_rows", "5").toIntOrNull() ?: 5) }
    var navigationMode by remember { mutableStateOf(db.get("navigation_mode", "gestures")) }
    var animationSpeed by remember { mutableStateOf(db.get("animation_speed", "Slow")) }
    var homeStyle by remember { mutableStateOf(db.get("home_style", "With App drawer")) }
    var minusScreen by remember { mutableStateOf(db.get("minus_screen", "Google Discover")) }
    var searchStyle by remember { mutableStateOf(db.get("search_style", "Full screen search")) }
    var swipeDownAction by remember {
        val savedSwipeAction = db.get("swipe_down_action", "Control Center")
        mutableStateOf(if (savedSwipeAction == "App finder") "Control Center" else savedSwipeAction)
    }
    var fillCells by remember { mutableStateOf(db.get("fill_cells", "true") == "true") }
    var lockLayout by remember { mutableStateOf(db.get("lock_layout", "false") == "true") }
    var hideText by remember { mutableStateOf(db.get("hide_text", "Off")) }
    val minHomeCellHeightDp = if(labels && hideText=="Off") 80f else 66f
    val screenHeightDp = context.resources.displayMetrics.heightPixels / density.density
    val availableGridHeightDp = (screenHeightDp - 270f).coerceAtLeast(minHomeCellHeightDp)
    val effectiveRows = minOf(rows,(availableGridHeightDp/minHomeCellHeightDp).toInt().coerceAtLeast(1))
    var recentsArrangement by remember { mutableStateOf(db.get("recents_arrangement", "Vertically")) }
    var highRefreshRate by remember { mutableStateOf(db.get("high_refresh_rate", "true") == "true") }
    LaunchedEffect(drawer,highRefreshRate) {
        if(drawer && highRefreshRate) activity.requestHighestRefreshRate()
    }
    var booting by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2850); booting=false }
    var homeEntryRevision by remember { mutableIntStateOf(0) }
    var homeEntryPage by remember { mutableIntStateOf(page) }
    LaunchedEffect(activity.homeUnlockRevision) {
        // Animate only the page visible at the moment the device finishes unlocking.
        if (activity.homeUnlockRevision > 0) {
            homeEntryPage = page
            homeEntryRevision++
            delay(900L)
            homeEntryRevision = 0
        }
    }
    LaunchedEffect(page) {
        // Leaving the unlock page consumes the one-shot animation; swiping back
        // must not replay it.
        if (homeEntryRevision > 0 && page != homeEntryPage) homeEntryRevision = 0
    }
    var deviceState by remember { mutableStateOf(DeviceStateReader.read(context)) }
    val bg = if (dark) Color(0xFF171923) else Color(0xFFF2F1F7)
    val fg = if (dark) Color.White else Color(0xFF18191F)
    val filtered = apps.filter { it.label.contains(search, true) }

    fun persistDock() { db.put("dock_packages", dockPackages.joinToString("|")) }
    fun persistFolders() { db.put("home_folders", encodeHomeFolders(folders)) }
    fun slotAt(position:Offset,pageIndex:Int=page):Int {
        val bounds=gridBoundsFor(pageIndex)
        val cellW=(bounds.width / columns.coerceAtLeast(1)).coerceAtLeast(1f)
        val cellH=(bounds.height / effectiveRows.coerceAtLeast(1)).coerceAtLeast(1f)
        val col=((position.x-bounds.left)/cellW).toInt().coerceIn(0,columns-1)
        val row=((position.y-bounds.top)/cellH).toInt().coerceIn(0,effectiveRows-1)
        return row*columns+col
    }
    fun widgetSlotAt(position:Offset,id:Int,pageIndex:Int=pendingDragPage):Int {
        val grid=gridBoundsFor(pageIndex)
        val cellW=(grid.width/columns.coerceAtLeast(1)).coerceAtLeast(1f)
        val cellH=(grid.height/effectiveRows.coerceAtLeast(1)).coerceAtLeast(1f)
        val widget=widgetCanvasBounds
        val spanX=if(widget.width>0f) kotlin.math.ceil(widget.width/cellW).toInt().coerceIn(1,columns) else 1
        val spanY=if(widget.height>0f) kotlin.math.ceil(widget.height/cellH).toInt().coerceIn(1,effectiveRows) else 1
        val col=(((position.x-grid.left)/cellW).toInt()).coerceIn(0,(columns-spanX).coerceAtLeast(0))
        val row=(((position.y-grid.top)/cellH).toInt()).coerceIn(0,(effectiveRows-spanY).coerceAtLeast(0))
        return row*columns+col
    }
    val showLabels = labels && hideText == "Off"
    val folderPackages = remember(folders) { folders.flatMap { it.packages }.toSet() }
    val pageAppSlices = remember(apps, homeWidgets, columns, rows, effectiveRows, showLabels, showWeatherWidget, showTodayWidget, weatherWidgetPage, todayWidgetPage, appPageAssignments, appSlotAssignments, folders, dockPackages) {
        val result = mutableMapOf<Int, MutableList<InstalledApp>>()
        var defaultPage = 0
        fun capacityFor(index: Int): Int {
            val densityScale=context.resources.displayMetrics.density.coerceAtLeast(1f)
            val screenWidthDp=context.resources.displayMetrics.widthPixels/densityScale
            val cellWidth=(screenWidthDp/columns.coerceAtLeast(1)).coerceAtLeast(1f)
            val cellHeight=(availableGridHeightDp/effectiveRows.coerceAtLeast(1)).coerceAtLeast(1f)
            val widgetCells=homeWidgets.filter { it.second==index }.sumOf { (id,_) ->
                val info=AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
                if(info==null) 4 else {
                    val spanX=kotlin.math.ceil(info.minWidth.coerceAtLeast(110)/cellWidth).toInt().coerceIn(1,columns)
                    val spanY=kotlin.math.ceil(info.minHeight.coerceAtLeast(90)/cellHeight) .toInt().coerceIn(1,effectiveRows)
                    spanX*spanY
                }
            }
            val builtInCells=(if(showWeatherWidget && weatherWidgetPage==index) minOf(2,effectiveRows)*minOf(2,columns) else 0)+(if(showTodayWidget && todayWidgetPage==index) minOf(2,effectiveRows)*minOf(2,columns) else 0)
            return (columns*effectiveRows.coerceAtLeast(1)-widgetCells-builtInCells-folders.count { it.page==index }).coerceAtLeast(1)
        }
        apps.filterNot { it.packageName in folderPackages || it.packageName in dockPackages }.forEach { app ->
            var destination=(appPageAssignments[app.packageName] ?: defaultPage).coerceAtLeast(0)
            while((result[destination]?.size ?: 0)>=capacityFor(destination)) destination++
            result.getOrPut(destination) { mutableListOf() }.add(app)
            if(appPageAssignments[app.packageName]==null) defaultPage=destination
        }
        val maxPage = maxOf(
            result.keys.maxOrNull() ?: 0,
            homeWidgets.maxOfOrNull { it.second } ?: 0,
            if (showWeatherWidget) weatherWidgetPage else 0,
            if (showTodayWidget) todayWidgetPage else 0,
            folders.maxOfOrNull { it.page } ?: 0
        )
        (0..maxPage).map { pageIndex ->
            val pageItems = result[pageIndex]?.toList().orEmpty()
            pageItems.sortedBy { app -> appSlotAssignments[app.packageName] ?: pageItems.indexOf(app) }
        }
    }
    fun updateGridDragPreview(position:Offset,movingKey:String,movingSpanX:Int=1,movingSpanY:Int=1) {
        val destinationGrid=gridBoundsFor(pendingDragPage)
        if(destinationGrid.width<=0f || destinationGrid.height<=0f) return
        val cw=(destinationGrid.width/columns.coerceAtLeast(1)).coerceAtLeast(1f)
        val ch=(destinationGrid.height/effectiveRows.coerceAtLeast(1)).coerceAtLeast(1f)
        val items=mutableListOf<DragGridPlacement>()
        // Build occupied app cells from the destination page's model, not from
        // currentPageAppKeys/appBounds (those can still describe the page we dragged from
        // while AnimatedContent is transitioning).
        val destinationApps=pageAppSlices.getOrNull(pendingDragPage.coerceAtLeast(0)).orEmpty()
        destinationApps.forEachIndexed { index, app ->
            val key="app:"+app.packageName
            if(key!=movingKey) {
                val requested=appSlotAssignments[app.packageName] ?: index
                items.add(DragGridPlacement(key,requested))
            }
        }
        folders.filter { it.page==pendingDragPage }.forEach { folder ->
            if("folder:"+folder.id!=movingKey) items.add(DragGridPlacement("folder:"+folder.id,folder.slot))
        }
        widgetBounds.forEach { (id,b) ->
            val widgetPage=when(id) {
                -1001 -> weatherWidgetPage
                -1002 -> todayWidgetPage
                else -> homeWidgets.firstOrNull { it.first==id }?.second
            }
            if(widgetPage==pendingDragPage && "widget:"+id!=movingKey && b.width>0f && b.height>0f)
                items.add(DragGridPlacement("widget:"+id,widgetSlotAssignments[id] ?: slotAt(b.center),
                    kotlin.math.ceil(b.width/cw).toInt().coerceIn(1,columns),
                    kotlin.math.ceil(b.height/ch).toInt().coerceIn(1,effectiveRows)))
        }
        val slot=if(movingKey.startsWith("widget:")) widgetSlotAt(position,movingKey.substringAfter(":").toIntOrNull() ?: -1,pendingDragPage) else slotAt(position,pendingDragPage)
        val moving=DragGridPlacement(movingKey,slot,movingSpanX.coerceIn(1,columns),movingSpanY.coerceIn(1,effectiveRows))
        val ordered=items.sortedWith(compareBy<DragGridPlacement>{it.slot}.thenBy{it.key}).toMutableList()
        val col=slot%columns.coerceAtLeast(1); val row=slot/columns.coerceAtLeast(1)
        val hit=ordered.indexOfFirst { col>=it.slot%columns && col<it.slot%columns+it.spanX && row>=it.slot/columns && row<it.slot/columns+it.spanY }
        val at=if(hit>=0) { val item=ordered[hit]; val cx=item.slot%columns+(item.spanX-1)/2; val cy=item.slot/columns+(item.spanY-1)/2
            val after=if(kotlin.math.abs(row-cy)>kotlin.math.abs(col-cx)) row>cy else col>cx
            (hit+if(after) 1 else 0).coerceIn(0,ordered.size)
        } else ordered.indexOfFirst { it.slot>slot }.let { if(it<0) ordered.size else it }
        ordered.add(at,moving)
        val placements = ordered.map { item ->
            HomeGridTile(item.key, item.slot, item.spanX, item.spanY)
        }
        dragPreviewSlots = HomeWorkspaceEngine.layout(placements, columns, effectiveRows, fillCells)
            .mapValues { (_, cell) -> cell.row * columns.coerceAtLeast(1) + cell.column }
    }
    fun applyGridDragPreview(destinationPage:Int) {
        val preview=dragPreviewSlots
        preview.forEach { (key,slot) -> when {
            key.startsWith("app:") -> { val pkg=key.substringAfter("app:"); appSlotAssignments=appSlotAssignments+(pkg to slot) }
            key.startsWith("widget:") -> { val id=key.substringAfter("widget:").toIntOrNull() ?: return@forEach; widgetSlotAssignments=widgetSlotAssignments+(id to slot) }
            key.startsWith("folder:") -> { val id=key.substringAfter("folder:"); folders=folders.map { if(it.id==id) it.copy(slot=slot) else it } }
        } }
        if(preview.keys.any { it.startsWith("folder:") }) persistFolders()
        db.put("app_slots",appSlotAssignments.entries.joinToString(";"){it.key+":"+it.value})
        db.put("app_pages",appPageAssignments.entries.joinToString(";"){it.key+":"+it.value})
        db.put("widget_slots",widgetSlotAssignments.entries.joinToString(";"){it.key.toString()+":"+it.value})
        dragPreviewSlots=emptyMap()
    }
    /**
     * Remove vacant workspace pages and compact the remaining pages without
     * changing the relative order of populated pages. Called only after a drag
     * commits, so a temporarily empty page during a drag is never deleted.
     */
    fun normalizeHomePages(
        movingPackage: String? = null,
        excludedPackage: String? = null,
        movingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        destinationPage: Int = page
    ) {
        val appPages = linkedMapOf<String, Int>()
        pageAppSlices.forEachIndexed { index, pageApps ->
            pageApps.forEach { app ->
                if (app.packageName != movingPackage && app.packageName != excludedPackage) appPages[app.packageName] = index
            }
        }
        if (movingPackage != null) appPages[movingPackage] = destinationPage.coerceAtLeast(0)

        val widgetPages: MutableMap<Int, Int> = homeWidgets.toMap().toMutableMap()
        if (movingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && movingWidgetId >= 0) {
            widgetPages[movingWidgetId] = destinationPage.coerceAtLeast(0)
        }
        val occupied = mutableSetOf(0)
        occupied.addAll(appPages.values)
        occupied.addAll(widgetPages.values)
        occupied.addAll(folders.map { it.page })
        if (showWeatherWidget) occupied.add(weatherWidgetPage)
        if (showTodayWidget) occupied.add(todayWidgetPage)
        val orderedPages = occupied.filter { it >= 0 }.distinct().sorted()
        val pageMap = orderedPages.mapIndexed { newIndex, oldIndex -> oldIndex to newIndex }.toMap()

        appPageAssignments = appPages.mapNotNull { (pkg, oldPage) ->
            pageMap[oldPage]?.let { pkg to it }
        }.toMap()
        homeWidgets = widgetPages.mapNotNull { (id, oldPage) ->
            pageMap[oldPage]?.let { id to it }
        }
        folders = folders.mapNotNull { folder ->
            pageMap[folder.page]?.let { folder.copy(page = it) }
        }
        weatherWidgetPage = pageMap[weatherWidgetPage] ?: 0
        todayWidgetPage = pageMap[todayWidgetPage] ?: 0
        extraHomePages = 0

        val oldCurrent = page.coerceAtLeast(0)
        page = pageMap[oldCurrent] ?: (pageMap.values.maxOrNull() ?: 0)
        pendingDragPage = page
        db.put("app_pages", appPageAssignments.entries.joinToString(";") { it.key + ":" + it.value })
        db.put("home_widgets", homeWidgets.joinToString(",") { it.first.toString() + ":" + it.second })
        db.put("home_folders", encodeHomeFolders(folders))
        db.put("weather_widget_page", weatherWidgetPage.toString())
        db.put("today_widget_page", todayWidgetPage.toString())
        db.put("extra_home_pages", "0")
    }
    fun finishWidgetDrag() {
        val id=draggingWidgetId
        val destinationPage=pendingDragPage.coerceAtLeast(0)
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID) return
        fun persistWidgetPositions() {
            db.put("widget_slots",widgetSlotAssignments.entries.joinToString(";"){it.key.toString()+":"+it.value})
            db.put("app_slots",appSlotAssignments.entries.joinToString(";"){it.key+":"+it.value})
            db.put("app_pages",appPageAssignments.entries.joinToString(";"){it.key+":"+it.value})
            db.put("home_widgets",homeWidgets.joinToString(","){it.first.toString()+":"+it.second})
        }
        if(widgetDragPosition.y<uninstallDropZoneHeight) {
            when(id) {
                -1001 -> { showWeatherWidget=false; db.put("show_weather_widget","false") }
                -1002 -> { showTodayWidget=false; db.put("show_today_widget","false") }
                else -> { activity.removeHomeWidget(id); homeWidgets=homeWidgets.filterNot { it.first==id } }
            }
            widgetSlotAssignments=widgetSlotAssignments-id
            widgetPositions=widgetPositions-id
        } else {
            // Recalculate using the destination page's current grid bounds and tile model;
            // the preview may have been cleared during the last edge-page transition.
            val destinationGrid=gridBoundsFor(destinationPage)
            val destinationCellW=(destinationGrid.width/columns.coerceAtLeast(1)).coerceAtLeast(1f)
            val destinationCellH=(destinationGrid.height/effectiveRows.coerceAtLeast(1)).coerceAtLeast(1f)
            val spanX=kotlin.math.ceil(widgetCanvasBounds.width/destinationCellW).toInt().coerceIn(1,columns)
            val spanY=kotlin.math.ceil(widgetCanvasBounds.height/destinationCellH).toInt().coerceIn(1,effectiveRows)
            updateGridDragPreview(widgetDragPosition,"widget:"+id,spanX,spanY)
            applyGridDragPreview(destinationPage)
            when(id) {
                -1001 -> { weatherWidgetPage=destinationPage; db.put("weather_widget_page",destinationPage.toString()) }
                -1002 -> { todayWidgetPage=destinationPage; db.put("today_widget_page",destinationPage.toString()) }
                else -> homeWidgets=homeWidgets.map { if(it.first==id) id to destinationPage else it }
            }
        }
        if(dragPreviewSlots.isNotEmpty()) applyGridDragPreview(destinationPage)
        persistWidgetPositions()
        normalizeHomePages(
            movingWidgetId=if(id >= 0 && homeWidgets.any { it.first == id }) id else AppWidgetManager.INVALID_APPWIDGET_ID,
            destinationPage=destinationPage
        )
        persistWidgetPositions()
        draggingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID
        dragPreviewSlots=emptyMap()
        dragEdgeDirection=0
        page=destinationPage
        pendingDragPage=page
    }
    val pageCount = maxOf(
        pageAppSlices.size,
        (homeWidgets.maxOfOrNull { it.second } ?: 0) + 1,
        (folders.maxOfOrNull { it.page } ?: 0) + 1,
        (if (showWeatherWidget) weatherWidgetPage else 0) + 1,
        (if (showTodayWidget) todayWidgetPage else 0) + 1,
        extraHomePages + 1,
        1
    )
    var lastEdgePageAdvanceAt by remember { mutableLongStateOf(0L) }
    fun lastHomePage():Int = maxOf(
        (pageAppSlices.size-1).coerceAtLeast(0),
        appPageAssignments.values.maxOrNull() ?: 0,
        homeWidgets.maxOfOrNull { it.second } ?: 0,
        if(showWeatherWidget) weatherWidgetPage else 0,
        if(showTodayWidget) todayWidgetPage else 0,
        folders.maxOfOrNull { it.page } ?: 0,
        0
    )
    fun advanceDragPage(direction:Int) {
        if(direction==0) return
        val next=(pendingDragPage+direction).coerceAtLeast(0)
        if(direction>0 && next>lastHomePage()) {
            extraHomePages=maxOf(extraHomePages,next-(pageAppSlices.size-1).coerceAtLeast(0))
            db.put("extra_home_pages",extraHomePages.toString())
        }
        pendingDragPage=next
        page=next
        dragPreviewSlots=emptyMap()
        appBounds.clear()
        widgetBounds.clear()
        folderBounds.clear()
    }
    fun updateDragEdge(position:Offset) {
        val width=activity.window.decorView.width.toFloat()
        val edge=with(density){56.dp.toPx()}
        val direction=when { position.x<=edge -> -1; position.x>=width-edge -> 1; else -> 0 }
        if(direction==0) {
            dragEdgeDirection=0
            lastEdgePageAdvanceAt=0L
            return
        }
        if(direction!=dragEdgeDirection) {
            dragEdgeDirection=direction
            lastEdgePageAdvanceAt=android.os.SystemClock.uptimeMillis()
            advanceDragPage(direction)
        }
    }
    LaunchedEffect(draggingApp?.packageName,draggingWidgetId,dragEdgeDirection) {
        if(dragEdgeDirection!=0 && (draggingApp!=null || draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID)) {
            while(dragEdgeDirection!=0 && (draggingApp!=null || draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID)) {
                kotlinx.coroutines.delay(520L)
                if(dragEdgeDirection!=0 && (draggingApp!=null || draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID)) {
                    advanceDragPage(dragEdgeDirection)
                    lastEdgePageAdvanceAt=android.os.SystemClock.uptimeMillis()
                }
            }
        }
    }
    LaunchedEffect(pendingDragPage,dragPosition,widgetDragPosition,draggingApp?.packageName,draggingWidgetId) {
        kotlinx.coroutines.delay(16L)
        val app=draggingApp
        if(app!=null) updateGridDragPreview(dragPosition,"app:"+app.packageName)
        else if(draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID) {
            val grid=gridBoundsFor(pendingDragPage)
            val cw=(grid.width/columns.coerceAtLeast(1)).coerceAtLeast(1f)
            val ch=(grid.height/effectiveRows.coerceAtLeast(1)).coerceAtLeast(1f)
            val sx=kotlin.math.ceil(widgetCanvasBounds.width/cw).toInt().coerceIn(1,columns)
            val sy=kotlin.math.ceil(widgetCanvasBounds.height/ch).toInt().coerceIn(1,effectiveRows)
            updateGridDragPreview(widgetDragPosition,"widget:"+draggingWidgetId,sx,sy)
        }
    }
    fun finishIconDrag() {
        val dragged=draggingApp ?: return
        if(draggingFromFolder && folderOverlayBounds.contains(dragPosition)) {
            draggingApp=null; draggingFromFolder=false; dragSourceFolderId=null; dragEdgeDirection=0; pendingDragPage=page
            return
        }
        val targetPage=pendingDragPage.coerceAtLeast(0)
        fun saveAssignments() {
            db.put("app_pages",appPageAssignments.entries.joinToString(";"){it.key+":"+it.value})
            db.put("app_slots",appSlotAssignments.entries.joinToString(";"){it.key+":"+it.value})
            db.put("widget_slots",widgetSlotAssignments.entries.joinToString(";"){it.key.toString()+":"+it.value})
        }
        fun removeFromSourceFolder() {
            val source=dragSourceFolderId
            if(source!=null) {
                val sourceFolder=folders.firstOrNull { it.id==source }
                val remaining=sourceFolder?.packages.orEmpty().filterNot { it==dragged.packageName }
                folders=folders.filterNot { it.id==source }.toMutableList().also { updated ->
                    if(remaining.size>1 && sourceFolder!=null) updated.add(sourceFolder.copy(packages=remaining))
                }
                if(remaining.size==1 && sourceFolder!=null) {
                    appPageAssignments=appPageAssignments+(remaining.first() to sourceFolder.page)
                    appSlotAssignments=appSlotAssignments+(remaining.first() to sourceFolder.slot)
                }
                persistFolders()
            }
        }
        if(dragPosition.y<uninstallDropZoneHeight && !dragged.packageName.startsWith("internal:")) {
            pendingUninstallApp=dragged
            draggingApp=null; draggingFromDock=false; draggingFromFolder=false; dragSourceFolderId=null; dragEdgeDirection=0; pendingDragPage=page
            return
        }
        if(dockBounds.contains(dragPosition)) {
            removeFromSourceFolder()
            val next=dockPackages.filterNot { it==dragged.packageName }.toMutableList()
            val targetIndex=(((dragPosition.x-dockBounds.left)/dockBounds.width.coerceAtLeast(1f))*5f).toInt().coerceIn(0,4)
            next.add(targetIndex.coerceAtMost(next.size),dragged.packageName)
            dockPackages=next.distinct().take(5); persistDock()
            appPageAssignments=appPageAssignments-dragged.packageName
            appSlotAssignments=appSlotAssignments-dragged.packageName
            saveAssignments()
            normalizeHomePages(excludedPackage=dragged.packageName, destinationPage=page)
            saveAssignments()
            draggingApp=null; draggingFromDock=false; draggingFromFolder=false; dragSourceFolderId=null; dragEdgeDirection=0; pendingDragPage=page
            return
        }
        val destinationFolderIds=folders.filter { it.page==targetPage }.map { it.id }.toSet()
        val targetFolder=folderBounds.entries.firstOrNull { it.key in destinationFolderIds && it.key!=dragged.packageName && it.value.contains(dragPosition) }?.key
        if(targetFolder!=null) {
            removeFromSourceFolder()
            folders=folders.map { folder ->
                if(folder.id==targetFolder) folder.copy(packages=(folder.packages+dragged.packageName).distinct())
                else folder.copy(packages=folder.packages.filterNot { it==dragged.packageName })
            }.distinctBy { it.id }
            persistFolders()
            appPageAssignments=appPageAssignments-dragged.packageName
            appSlotAssignments=appSlotAssignments-dragged.packageName
        } else {
            val destinationAppKeys=pageAppSlices.getOrNull(targetPage).orEmpty().map { it.packageName }.toSet()
            val targetApp=if(targetPage==page) appBounds.entries.firstOrNull {
                it.key!=dragged.packageName && it.key in destinationAppKeys && it.value.contains(dragPosition)
            }?.key else null
            if(targetApp!=null) {
                val targetBounds=appBounds[targetApp] ?: Rect.Zero
                val sourceBounds=dragOriginBounds.takeIf { it.width>0f && it.height>0f } ?: Rect(dragPosition.x-36f,dragPosition.y-36f,dragPosition.x+36f,dragPosition.y+36f)
                val movingRect=Rect(dragPosition.x-sourceBounds.width/2f,dragPosition.y-sourceBounds.height/2f,dragPosition.x+sourceBounds.width/2f,dragPosition.y+sourceBounds.height/2f)
                val overlap=movingRect.intersect(targetBounds)
                val overlapArea=overlap.width.coerceAtLeast(0f)*overlap.height.coerceAtLeast(0f)
                val minArea=minOf(movingRect.width*movingRect.height,targetBounds.width*targetBounds.height).coerceAtLeast(1f)
                val centered=targetBounds.contains(dragPosition)
                val createFolder=centered && overlapArea/minArea>=0.62f &&
                    kotlin.math.abs(dragPosition.x-targetBounds.center.x)<=targetBounds.width*.30f &&
                    kotlin.math.abs(dragPosition.y-targetBounds.center.y)<=targetBounds.height*.30f
                if(createFolder) {
                    removeFromSourceFolder()
                    val existingFolder=folders.firstOrNull { targetApp in it.packages }
                    val targetPageOld=appPageAssignments[targetApp] ?: targetPage
                    val targetSlotOld=appSlotAssignments[targetApp] ?: slotAt(targetBounds.center)
                    folders=folders.map { folder -> folder.copy(packages=folder.packages.filterNot { it==dragged.packageName }) }
                    if(existingFolder!=null) folders=folders.map { folder -> if(folder.id==existingFolder.id) folder.copy(packages=(folder.packages+dragged.packageName).distinct()) else folder }
                    else { val targetModel=apps.firstOrNull { it.packageName==targetApp }; folders=folders+HomeFolder("folder:"+targetApp,targetModel?.label ?: "Folder",listOf(targetApp,dragged.packageName).distinct(),targetPageOld,targetSlotOld) }
                    folders=folders.filter { it.packages.isNotEmpty() }.distinctBy { it.id }
                    persistFolders()
                    appPageAssignments=appPageAssignments-dragged.packageName-targetApp
                    appSlotAssignments=appSlotAssignments-dragged.packageName-targetApp
                } else {
                    removeFromSourceFolder()
                    // Rebuild placement for this destination page as well when the
                    // pointer is released directly over an app without creating a folder.
                    updateGridDragPreview(dragPosition,"app:"+dragged.packageName)
                    applyGridDragPreview(targetPage)
                    appPageAssignments=appPageAssignments+(dragged.packageName to targetPage)
                }
            } else {
                removeFromSourceFolder()
                // Rebuild the final preview against the destination page at drop time.
                // Edge paging clears transient previews; without this, a release immediately
                // after a page transition can reuse the source page's/default slot.
                updateGridDragPreview(dragPosition,"app:"+dragged.packageName)
                applyGridDragPreview(targetPage)
                appPageAssignments=appPageAssignments+(dragged.packageName to targetPage)
            }
        }
        if(draggingFromDock) dockPackages=dockPackages.filterNot { it==dragged.packageName }
        dockPackages=dockPackages.distinct()
        persistDock()
        saveAssignments()
        dragPreviewSlots=emptyMap()
        page=targetPage
        val isNowInFolder = folders.any { dragged.packageName in it.packages }
        normalizeHomePages(
            movingPackage=if (isNowInFolder || dragged.packageName in dockPackages) null else dragged.packageName,
            excludedPackage=if (isNowInFolder || dragged.packageName in dockPackages) dragged.packageName else null,
            destinationPage=targetPage
        )
        saveAssignments()
        draggingApp=null; draggingFromDock=false; draggingFromFolder=false; dragSourceFolderId=null; dragEdgeDirection=0; pendingDragPage=page
    }

    SideEffect {
        activity.window.statusBarColor = if (settings) android.graphics.Color.BLACK else android.graphics.Color.rgb(37, 38, 94)
        activity.window.navigationBarColor = if (settings) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
        val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
    }
    LaunchedEffect(settings, drawer, controlCenter, openFolderId) {
        // Keep Android in three-button navigation mode. Hide the navigation bar
        // while the launcher is visible; a bottom-edge swipe reveals the system
        // buttons transiently so they remain available without occupying screen space.
        val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (settings || drawer || controlCenter || openFolderId != null) {
            bars.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        } else {
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }
    LaunchedEffect(pageCount) { if (page >= pageCount) page = pageCount - 1 }
    LaunchedEffect(page) { appBounds.clear(); widgetBounds.clear(); folderBounds.clear() }
    LaunchedEffect(apps,folders,dockPackages) {
        val installed=apps.map { it.packageName }.toSet()
        val seen=mutableSetOf<String>()
        val cleaned=folders.mapNotNull { folder ->
            val members=folder.packages.filter { it in installed && seen.add(it) }.distinct()
            when {
                members.size>1 -> folder.copy(packages=members)
                members.size==1 -> {
                    appPageAssignments=appPageAssignments+(members.first() to folder.page)
                    appSlotAssignments=appSlotAssignments+(members.first() to folder.slot)
                    null
                }
                else -> null
            }
        }
        if(cleaned!=folders) { folders=cleaned; db.put("home_folders",encodeHomeFolders(cleaned)) }
        val safeDock=dockPackages.distinct().filter { it in installed && it !in seen }.take(5)
        if(safeDock!=dockPackages) { dockPackages=safeDock; db.put("dock_packages",safeDock.joinToString("|")) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            deviceState = DeviceStateReader.read(context)
            val persistedWidgets = db.get("home_widgets").split(",").mapNotNull { token ->
                val parts = token.split(":")
                val id = parts.getOrNull(0)?.toIntOrNull()
                val widgetPage = parts.getOrNull(1)?.toIntOrNull()
                if (id != null && widgetPage != null) id to widgetPage else null
            }
            if (persistedWidgets != homeWidgets) homeWidgets = persistedWidgets
            kotlinx.coroutines.delay(1200)
        }
    }

    // Keep the launcher surface transparent so only the Android system wallpaper is visible.
    Box(
        Modifier.fillMaxSize().background(Color.Transparent)
        .pointerInput(drawer, controlCenter, settings, navigationMode, homeStyle, swipeDownAction, minusScreen, resizingWidgetId) {
            if (!drawer && !controlCenter && !settings && openFolderId==null && draggingApp == null && draggingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && resizingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Leave Android's back and home gesture edge regions untouched.
                    val systemEdge = with(density) { 32.dp.toPx() }
                    if (down.position.x <= systemEdge ||
                        down.position.x >= size.width - systemEdge ||
                        down.position.y >= size.height - systemEdge
                    ) return@awaitEachGesture
                    var dx = 0f
                    var dy = 0f
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (draggingApp != null || draggingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID || resizingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) { finished=true; break }
                        if (!change.pressed) break
                        val delta = change.position - change.previousPosition
                        dx += delta.x
                        dy += delta.y
                        val threshold = viewConfiguration.touchSlop * 1.4f
                        if (kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f) {
                            if (dx > 0 && page == 0 && minusScreen == "Google Discover") page = -1
                            else page = (page + if (dx < 0) 1 else -1).coerceIn(0, pageCount - 1)
                            change.consume()
                            finished = true
                        } else if (kotlin.math.abs(dy) > threshold && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) {
                            val topRightEdge = down.position.y < size.height * .16f && down.position.x > size.width * .62f
                            if (dy > 0 && topRightEdge && swipeDownAction == "Control Center") {
                                controlCenter = true
                                change.consume()
                                finished = true
                            } else if (dy < 0 && down.position.y > size.height * .86f && homeStyle == "With App drawer") {
                                drawer = true
                                change.consume()
                                finished = true
                            } else {
                                finished = true
                            }
                        }
                    }
                }
            }
        }
             .pointerInput(apps, folders, dockPackages, columns, rows, drawer, controlCenter, settings) {
                if (!drawer && !controlCenter && !settings && openFolderId==null ) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        val globalDown=down.position
                        val appKey=appBounds.entries.firstOrNull { it.value.contains(globalDown) }?.key
                        val widgetKey=widgetBounds.entries.firstOrNull { entry ->
                            entry.value.contains(globalDown) && widgetResizeHandleBounds[entry.key]?.contains(globalDown)!=true &&
                                (entry.key<0 || widgetHandleBounds[entry.key]?.contains(globalDown)==true)
                        }?.key
                        if(appKey==null && widgetKey==null) return@awaitEachGesture
                        val longPress=awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        when {
                            appKey!=null -> {
                                val app=apps.firstOrNull { it.packageName==appKey }
                                if(app!=null) {
                                    draggingApp=app
                                    draggingFromDock=false
                                    draggingFromFolder=false
                                    dragSourceFolderId=null
                                    dragPosition=longPress.position
                                    dragOriginBounds=appBounds[appKey] ?: Rect.Zero
                                    dragOriginPage=page
                                    dragOriginSlot=appSlotAssignments[appKey] ?: slotAt(dragOriginBounds.center)
                                    pendingDragPage=page
                                }
                            }
                            widgetKey!=null -> {
                                draggingWidgetId=widgetKey
                                widgetCanvasBounds=widgetBounds[widgetKey] ?: Rect.Zero
                                widgetDragPosition=longPress.position
                                pendingDragPage=page
                            }
                        }
                        var endedNormally=false
                        while(true) {
                            // Pointer events keep the drag responsive; edge paging advances
                            // as the user continues moving along the screen edge.
                            val event=awaitPointerEvent(PointerEventPass.Initial)
                            val change=event.changes.firstOrNull { it.id==down.id } ?: break
                            if(!change.pressed) {
                                endedNormally=true
                                break
                            }
                            val delta=change.position-change.previousPosition
                            if(draggingApp!=null) {
                                change.consume()
                                dragPosition+=delta
                                updateDragEdge(dragPosition)
                                draggingApp?.let { updateGridDragPreview(dragPosition,"app:"+it.packageName) }
                            } else if(draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID) {
                                change.consume()
                                widgetDragPosition+=delta
                                updateDragEdge(widgetDragPosition)
                                val sx=kotlin.math.ceil(widgetCanvasBounds.width/(homeGridBounds.width/columns.coerceAtLeast(1)).coerceAtLeast(1f)).toInt().coerceIn(1,columns)
                                val sy=kotlin.math.ceil(widgetCanvasBounds.height/(homeGridBounds.height/effectiveRows.coerceAtLeast(1)).coerceAtLeast(1f)).toInt().coerceIn(1,effectiveRows)
                                updateGridDragPreview(widgetDragPosition,"widget:"+draggingWidgetId,sx,sy)
                            }
                        }
                        if(endedNormally) {
                            if(draggingApp!=null) finishIconDrag()
                            else if(draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID) finishWidgetDrag()
                        } else {
                            // A cancelled pointer sequence must leave no transient drag state
                            // behind; otherwise the next gesture can inherit a stale preview.
                            draggingApp=null
                            draggingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID
                            draggingFromDock=false
                            draggingFromFolder=false
                            dragSourceFolderId=null
                            dragPreviewSlots=emptyMap()
                            dragEdgeDirection=0
                            pendingDragPage=page
                        }
                    }
                }
            }
    ) {
        if (!drawer) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).graphicsLayer {
            renderEffect = if ((controlCenter || openFolderId!=null) && android.os.Build.VERSION.SDK_INT >= 31) BlurEffect(18f, 18f) else null
        }) {
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.combinedClickable(onClick={}, onLongClick={ if(!lockLayout) homeEditMode=true })) {
                    Text(greeting(), color = fg.copy(alpha=.72f), fontSize=14.sp)
                    Text(SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()), color = fg, fontSize=24.sp, fontWeight=FontWeight.SemiBold)
                }
                Surface(shape=CircleShape, color=Color.White.copy(alpha=.5f), modifier=Modifier.size(42.dp).clickable { controlCenter=true }) {
                    Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Tune, null, tint=fg) }
                }
            }
            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (androidx.compose.animation.slideInHorizontally(spring(dampingRatio=.86f,stiffness=when(animationSpeed) { "Slow" -> 260f; "Normal" -> 430f; else -> 720f })) { if (targetState > initialState) it else -it } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(110))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(spring(dampingRatio=.9f,stiffness=when(animationSpeed) { "Slow" -> 300f; "Normal" -> 500f; else -> 800f })) { if (targetState > initialState) -it else it } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)))
                },
                label = "home-pages"
            ) { targetPage ->
                val pageApps = if (targetPage < 0) emptyList() else pageAppSlices.getOrNull(targetPage).orEmpty()
                SideEffect {
                    val visibleKeys=pageApps.map { it.packageName }.toSet()
                    if(currentPageAppKeys!=visibleKeys) {
                        currentPageAppKeys=visibleKeys
                        // Drop stale hitboxes from pages that are no longer visible.
                        appBounds.keys.filterNot { it in visibleKeys }.forEach { appBounds.remove(it) }
                    }
                }
                if (targetPage < 0 && minusScreen == "Google Discover") {
                    val googleApp = apps.firstOrNull { it.packageName == "com.google.android.googlequicksearchbox" }
                    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("Google Discover",color=fg,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                        Text("Open the Google app to view your Discover feed.",color=fg.copy(alpha=.7f),fontSize=14.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick={googleApp?.let { activity.launch(it) }}) { Text("Open Google") }
                    }
                } else Column(Modifier.fillMaxSize()) {
                                        BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth()
                            .onGloballyPositioned {
                                val measured=it.boundsInRoot()
                                homeGridBoundsByPage[targetPage]=measured
                                if(targetPage==page || homeGridBounds.width<=0f) homeGridBounds=measured
                            }

                    ) {
                        val cellW=maxWidth/columns.coerceAtLeast(1)
                        val cellH=maxHeight/effectiveRows.coerceAtLeast(1)
                        val visibleFolders=folders.filter { it.page==targetPage }
                        val widgetInfoMap=homeWidgets.filter { it.second==targetPage }.mapNotNull { (id,_) ->
                            AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.let { id to it }
                        }.toMap()
                        val gridTiles=buildList {
                            pageApps.forEachIndexed { index, app ->
                                add(HomeGridTile("app:"+app.packageName,dragPreviewSlots["app:"+app.packageName] ?: appSlotAssignments[app.packageName] ?: index))
                            }
                            // Include a moving app as a real destination-page tile even though
                            // its persisted page assignment is not committed until drop.
                            draggingApp?.takeIf { targetPage==pendingDragPage && pageApps.none { app -> app.packageName==it.packageName } }?.let { moving ->
                                add(HomeGridTile("app:"+moving.packageName,dragPreviewSlots["app:"+moving.packageName] ?: dragPreviewSlots.size))
                            }
                            visibleFolders.forEach { folder -> add(HomeGridTile("folder:"+folder.id,dragPreviewSlots["folder:"+folder.id] ?: folder.slot)) }
                            if(showWeatherWidget && targetPage==weatherWidgetPage) add(HomeGridTile("widget:-1001",dragPreviewSlots["widget:-1001"] ?: widgetSlotAssignments[-1001] ?: pageApps.size+visibleFolders.size, minOf(2,columns),minOf(2,effectiveRows)))
                            if(showTodayWidget && targetPage==todayWidgetPage) add(HomeGridTile("widget:-1002",dragPreviewSlots["widget:-1002"] ?: widgetSlotAssignments[-1002] ?: pageApps.size+visibleFolders.size+1, minOf(2,columns),minOf(2,effectiveRows)))
                            widgetInfoMap.forEach { (id,info) ->
                                val custom=widgetSizes[id] ?: (info.minWidth.coerceAtLeast(110).dp to info.minHeight.coerceAtLeast(90).dp)
                                val spanX=kotlin.math.ceil(custom.first.value/cellW.value.coerceAtLeast(1f)).toInt().coerceIn(1,columns)
                                val spanY=kotlin.math.ceil(custom.second.value/cellH.value.coerceAtLeast(1f)) .toInt().coerceIn(1,effectiveRows)
                                add(HomeGridTile("widget:"+id,dragPreviewSlots["widget:"+id] ?: widgetSlotAssignments[id] ?: pageApps.size+visibleFolders.size+2+homeWidgets.indexOfFirst { it.first==id },spanX,spanY))
                            }
                            // A widget crossing pages must reserve its full footprint on
                            // the destination page while it is still being dragged.
                            if(draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID && targetPage==pendingDragPage &&
                                none { it.key=="widget:"+draggingWidgetId }) {
                                val bounds=widgetBounds[draggingWidgetId] ?: widgetCanvasBounds
                                val destinationGrid=gridBoundsFor(targetPage)
                                val destinationCellW=(destinationGrid.width/columns.coerceAtLeast(1)).coerceAtLeast(1f)
                                val destinationCellH=(destinationGrid.height/effectiveRows.coerceAtLeast(1)).coerceAtLeast(1f)
                                val spanX=if(bounds.width>0f) kotlin.math.ceil(bounds.width/destinationCellW).toInt().coerceIn(1,columns) else 2.coerceAtMost(columns)
                                val spanY=if(bounds.height>0f) kotlin.math.ceil(bounds.height/destinationCellH).toInt().coerceIn(1,effectiveRows) else 2.coerceAtMost(effectiveRows)
                                add(HomeGridTile("widget:"+draggingWidgetId,dragPreviewSlots["widget:"+draggingWidgetId] ?: dragPreviewSlots.size,spanX,spanY))
                            }
                        }
                        val gridCells=remember(gridTiles,columns,effectiveRows,fillCells) { calculateHomeGridCells(gridTiles,columns,effectiveRows,fillCells) }
                        LaunchedEffect(targetPage,gridCells,fillCells,draggingApp,draggingWidgetId) {
                            if(fillCells && draggingApp==null && draggingWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID) {
                                val appUpdates=mutableMapOf<String,Int>()
                                pageApps.forEach { app ->
                                    val cell=gridCells["app:"+app.packageName]
                                    if(cell!=null) appUpdates[app.packageName]=cell.row*columns+cell.column
                                }
                                val widgetUpdates=mutableMapOf<Int,Int>()
                                gridTiles.filter { it.key.startsWith("widget:") }.forEach { tile ->
                                    val id=tile.key.removePrefix("widget:").toIntOrNull()
                                    val cell=gridCells[tile.key]
                                    if(id!=null && cell!=null) widgetUpdates[id]=cell.row*columns+cell.column
                                }
                                if(appUpdates.isNotEmpty()) {
                                    appSlotAssignments=appSlotAssignments+appUpdates
                                    db.put("app_slots",appSlotAssignments.entries.joinToString(";"){it.key+":"+it.value})
                                }
                                if(widgetUpdates.isNotEmpty()) {
                                    widgetSlotAssignments=widgetSlotAssignments+widgetUpdates
                                    db.put("widget_slots",widgetSlotAssignments.entries.joinToString(";"){it.key.toString()+":"+it.value})
                                }
                            }
                        }
                        pageApps.forEachIndexed { iconIndex, app ->
                            val cell=gridCells["app:"+app.packageName] ?: return@forEachIndexed
                            val animatedX by animateDpAsState(cellW*cell.column,animationSpec=spring(dampingRatio=.78f,stiffness=430f),label="app-x")
                            val animatedY by animateDpAsState(cellH*cell.row,animationSpec=spring(dampingRatio=.78f,stiffness=430f),label="app-y")
                            HomeEntryMotion(
                                revision=if (targetPage == homeEntryPage) homeEntryRevision else 0,
                                order=iconIndex,
                                modifier=Modifier.offset(x=animatedX,y=animatedY).size(cellW,cellH)
                                    .graphicsLayer { alpha=if(draggingApp?.packageName==app.packageName) .10f else 1f }
                            ) {
                                AppIcon(
                                    app=app,labels=labels && hideText=="Off",fg=fg,index=0,animationSpeed=animationSpeed,
                                    dragTranslation=Offset.Zero,isDragging=false,
                                    size=62.dp,
                                    draggableEnabled=false,
                                    animateOnEntry=false,
                                    onBounds={ key,rect -> appBounds[key]=rect },
                                    onClick={ if(app.packageName=="internal:settings") settings=true else activity.launch(app) }
                                )
                            }
                        }
                        visibleFolders.forEachIndexed { folderIndex, folder ->
                            val cell=gridCells["folder:"+folder.id] ?: return@forEachIndexed
                            val animatedX by animateDpAsState(cellW*cell.column,animationSpec=spring(dampingRatio=.78f,stiffness=430f),label="folder-x")
                            val animatedY by animateDpAsState(cellH*cell.row,animationSpec=spring(dampingRatio=.78f,stiffness=430f),label="folder-y")
                            HomeEntryMotion(
                                revision=if (targetPage == homeEntryPage) homeEntryRevision else 0,
                                order=pageApps.size+folderIndex,
                                modifier=Modifier.offset(x=animatedX,y=animatedY).size(cellW,cellH)
                                    .onGloballyPositioned { folderBounds[folder.id]=it.boundsInRoot() }
                            ) {
                                FolderTile(folder,apps,fg,onClick={openFolderId=folder.id})
                            }
                        }
                        if(showWeatherWidget && targetPage==weatherWidgetPage) {
                            val cell=gridCells["widget:-1001"]
                            if(cell!=null) {
                                val wx by animateDpAsState(cellW*cell.column,animationSpec=spring(dampingRatio=.78f,stiffness=380f),label="weather-widget-x")
                                val wy by animateDpAsState(cellH*cell.row,animationSpec=spring(dampingRatio=.78f,stiffness=380f),label="weather-widget-y")
                                Box(Modifier.offset(x=wx,y=wy) .size(cellW*minOf(2,columns),cellH*minOf(2,effectiveRows)).graphicsLayer { alpha=if(draggingWidgetId==-1001) .12f else 1f }
                                .onGloballyPositioned { widgetBounds[-1001]=it.boundsInRoot(); if(draggingWidgetId==-1001) widgetCanvasBounds=it.boundsInRoot() }) {
                                HomeWidget("WEATHER","24°","Mostly Sunny",Icons.Default.WbSunny,Modifier.fillMaxWidth().padding(end=8.dp),Color(0xFFFFB648))
                                }
                            }
                        }
                        if(showTodayWidget && targetPage==todayWidgetPage) {
                            val cell=gridCells["widget:-1002"]
                            if(cell!=null) {
                                val wx by animateDpAsState(cellW*cell.column,animationSpec=spring(dampingRatio=.78f,stiffness=380f),label="today-widget-x")
                                val wy by animateDpAsState(cellH*cell.row,animationSpec=spring(dampingRatio=.78f,stiffness=380f),label="today-widget-y")
                                Box(Modifier.offset(x=wx,y=wy) .size(cellW*minOf(2,columns),cellH*minOf(2,effectiveRows)).graphicsLayer { alpha=if(draggingWidgetId==-1002) .12f else 1f }
                                .onGloballyPositioned { widgetBounds[-1002]=it.boundsInRoot(); if(draggingWidgetId==-1002) widgetCanvasBounds=it.boundsInRoot() }) {
                                HomeWidget("TODAY",SimpleDateFormat("d",Locale.getDefault()).format(Date()),
                                    SimpleDateFormat("EEEE",Locale.getDefault()).format(Date()).uppercase(Locale.getDefault()),
                                    Icons.Default.CalendarMonth,Modifier.fillMaxWidth().padding(start=8.dp),Color(0xFFEF6375))
                                }
                            }
                        }
                        widgetInfoMap.forEach { (widgetId,info) ->
                            val cell=gridCells["widget:"+widgetId] ?: return@forEach
                            val custom=widgetSizes[widgetId] ?: ((cellW*minOf(2,columns)).coerceAtLeast(110.dp) to info.minHeight.coerceAtLeast(90).dp)
                            val spanX=kotlin.math.ceil(custom.first.value/cellW.value.coerceAtLeast(1f)).toInt().coerceIn(1,columns)
                            val spanY=kotlin.math.ceil(custom.second.value/cellH.value.coerceAtLeast(1f)).toInt().coerceIn(1,effectiveRows)
                            val wx by animateDpAsState(cellW*cell.column,animationSpec=spring(dampingRatio=.78f,stiffness=380f),label="widget-x-$widgetId")
                            val wy by animateDpAsState(cellH*cell.row,animationSpec=spring(dampingRatio=.78f,stiffness=380f),label="widget-y-$widgetId")
                            Box(Modifier.offset(x=wx,y=wy).size(cellW*spanX,cellH*spanY).graphicsLayer { alpha=if(draggingWidgetId==widgetId) .12f else 1f }
                                .onGloballyPositioned { widgetBounds[widgetId]=it.boundsInRoot(); if(draggingWidgetId==widgetId) widgetCanvasBounds=it.boundsInRoot() }) {
                                AndroidView(factory={ activity.widgetHost.createView(it,widgetId,info) },modifier=Modifier.fillMaxSize())
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp)
                                        .onGloballyPositioned { widgetHandleBounds[widgetId]=it.boundsInRoot() }
                                        .clip(CircleShape).background(Color(0x88333333)),
                                    contentAlignment=Alignment.Center
                                ) { Text("⠿",color=Color.White,fontSize=19.sp) }
                                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(28.dp)
                                    .onGloballyPositioned { widgetResizeHandleBounds[widgetId]=it.boundsInRoot() }
                                    .clip(CircleShape).background(Color(0x88333333)).pointerInput(widgetId,cell) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart={ resizingWidgetId=widgetId },
                                            onDrag={ change,amount ->
                                                change.consume()
                                                val current=widgetSizes[widgetId] ?: (custom.first to custom.second)
                                                val maxWidth=cellW*(columns-cell.column).coerceAtLeast(1)
                                                val maxHeight=cellH*(effectiveRows-cell.row).coerceAtLeast(1)
                                                val w=(current.first+with(density){amount.x.toDp()}).coerceIn(110.dp,maxWidth.coerceAtLeast(110.dp))
                                                val h=(current.second+with(density){amount.y.toDp()}).coerceIn(90.dp,maxHeight.coerceAtLeast(90.dp))
                                                widgetSizes=widgetSizes+(widgetId to (w to h))
                                                db.put("home_widget_sizes",widgetSizes.entries.joinToString(";"){it.key.toString()+":"+it.value.first.value+":"+it.value.second.value})
                                            },
                                            onDragEnd={ resizingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID },
                                            onDragCancel={ resizingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID }
                                        )
                                    }) {
                                    Icon(Icons.Default.OpenInFull,null,tint=Color.White,modifier=Modifier.padding(5.dp))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp), horizontalArrangement=Arrangement.Center) {
                repeat(pageCount) { index ->
                    Box(Modifier.padding(horizontal=4.dp).size(if(page==index) 9.dp else 7.dp)
                        .clip(CircleShape).background(if(page==index) Color(0xFF1685F8) else fg.copy(alpha=.24f))
                        .clickable { page=index })
                }
            }
            AnimatedVisibility(visible=homeEditMode, enter=fadeIn(tween(140))+scaleIn(spring(dampingRatio=.86f,stiffness=520f)), exit=fadeOut(tween(100))) {
                Surface(
                    modifier=Modifier.fillMaxWidth().padding(bottom=10.dp),
                    shape=RoundedCornerShape(22.dp),
                    color=if(dark) Color(0xDD252936) else Color(0xEFFFFFFF),
                    border=androidx.compose.foundation.BorderStroke(1.dp,fg.copy(alpha=.10f)),
                    shadowElevation=8.dp
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),
                        horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={ widgetProviders=activity.availableHomeWidgets(); widgetPickerOpen=true }) {
                            Icon(Icons.Default.Widgets,null); Spacer(Modifier.width(3.dp)); Text("Widgets")
                        }
                        TextButton(onClick={ settings=true }) {
                            Icon(Icons.Default.Settings,null); Spacer(Modifier.width(3.dp)); Text("Settings")
                        }
                        TextButton(onClick={
                            val current=page
                            val occupied=pageAppSlices.getOrNull(current).orEmpty().isNotEmpty() ||
                                homeWidgets.any { it.second==current } || folders.any { it.page==current } ||
                                (showWeatherWidget && weatherWidgetPage==current) || (showTodayWidget && todayWidgetPage==current)
                            if(!occupied && pageCount>1 && current==pageCount-1) {
                                extraHomePages=(extraHomePages-1).coerceAtLeast(0)
                                db.put("extra_home_pages",extraHomePages.toString())
                                page=(page-1).coerceAtLeast(0)
                            }
                        },enabled=pageCount>1) { Icon(Icons.Default.RemoveCircleOutline,null); Text("Page") }
                        TextButton(onClick={
                            val newPage=pageCount
                            extraHomePages+=1
                            db.put("extra_home_pages",extraHomePages.toString())
                            page=newPage
                        }) {
                            Icon(Icons.Default.AddCircleOutline,null); Text("Add")
                        }
                        TextButton(onClick={homeEditMode=false}) { Icon(Icons.Default.Check,null); Text("Done") }
                    }
                }
            }
            Surface(shape=RoundedCornerShape(30.dp), color=Color.White.copy(alpha=if (dark) .16f else .52f),
                modifier=Modifier.fillMaxWidth().padding(bottom=16.dp).border(1.dp,Color.White.copy(alpha=.45f),RoundedCornerShape(30.dp))
                    .onGloballyPositioned { dockBounds=it.boundsInRoot() }) {
                Row(Modifier.padding(horizontal=8.dp, vertical=10.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    dockPackages.forEach { packageName ->
                        val dockApp=apps.firstOrNull { it.packageName==packageName }
                        if(dockApp!=null) {
                            HomeEntryMotion(
                                revision=homeEntryRevision,
                                order=apps.size+folders.size+dockPackages.indexOf(packageName),
                                modifier=Modifier
                            ) {
                                AppIcon(
                                    app=dockApp,labels=false,fg=fg,index=0,animationSpeed="Fast",size=50.dp,
                                    animateOnEntry=false,
                                    onBounds={ key,rect -> dockAppBounds[key]=rect },
                                    onDragStart={
                                        draggingApp=dockApp
                                        draggingFromDock=true
                                        draggingFromFolder=false
                                        dragSourceFolderId=null
                                        dragPosition=dockAppBounds[packageName]?.center ?: dockBounds.center
                                        dragOriginBounds=dockAppBounds[packageName] ?: Rect.Zero
                                        dragOriginPage=page
                                        dragOriginSlot=appSlotAssignments[packageName] ?: slotAt(dragOriginBounds.center)
                                        pendingDragPage=page
                                    },
                                    onDrag={ delta -> dragPosition+=delta; updateDragEdge(dragPosition); updateGridDragPreview(dragPosition,"app:"+dockApp.packageName) },
                                    onDragEnd={ finishIconDrag() },
                                    onClick={ if(dockApp.packageName=="internal:settings") settings=true else activity.launch(dockApp) }
                                )
                            }
                        }
                    }
                    if(dockPackages.isEmpty()) {
                        Text("Hold an app and drag it here",color=fg.copy(alpha=.6f),fontSize=10.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom=4.dp), contentAlignment=Alignment.Center) {
                Box(Modifier.width(108.dp).height(5.dp).clip(CircleShape).background(fg.copy(alpha=.5f)).clickable { if(homeStyle=="With App drawer") drawer=true })
            }
        }
        }

        if(draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID) {
            val ghostWidth=with(density){widgetCanvasBounds.width.toDp()}.coerceIn(112.dp,340.dp)
            val ghostHeight=with(density){widgetCanvasBounds.height.toDp()}.coerceIn(88.dp,360.dp)
            val halfWidth=with(density){ghostWidth.toPx()/2f}
            val halfHeight=with(density){ghostHeight.toPx()/2f}
            val label=when(draggingWidgetId) {
                -1001 -> "WEATHER"
                -1002 -> "TODAY"
                else -> try { AppWidgetManager.getInstance(context).getAppWidgetInfo(draggingWidgetId)?.loadLabel(context.packageManager)?.toString() ?: "Widget" } catch (_:Exception) { "Widget" }
            }
            Surface(
                shape=RoundedCornerShape(26.dp),color=Color.White.copy(alpha=.86f),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.85f)),shadowElevation=24.dp,
                modifier=Modifier.offset { IntOffset((widgetDragPosition.x-halfWidth).toInt(),(widgetDragPosition.y-halfHeight).toInt()) }
                    .width(ghostWidth).height(ghostHeight).graphicsLayer { scaleX=.97f; scaleY=.97f; alpha=.94f }
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    Text(label,color=Color(0xFF5C6470),fontSize=11.sp,fontWeight=FontWeight.Bold)
                    Text(if(draggingWidgetId==-1001) "24°  Mostly Sunny" else if(draggingWidgetId==-1002) SimpleDateFormat("d MMM",Locale.getDefault()).format(Date()) else "Move widget",
                        color=Color(0xFF252B34),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
        BackHandler(enabled=true) {
            when {
                controlCenter -> Unit
                homeEditMode -> homeEditMode=false
                openFolderId!=null -> openFolderId=null
                drawer -> drawer=false
                settings -> settings=false
                draggingApp != null -> draggingApp=null
                else -> Unit
            }
        }
        AnimatedVisibility(
            visible=drawer,
            enter=slideInVertically(
                animationSpec=spring(dampingRatio=.86f,stiffness=420f),
                initialOffsetY={ fullHeight -> fullHeight }
            ) + fadeIn(tween(220)) + scaleIn(spring(dampingRatio=.88f,stiffness=440f),initialScale=.965f),
            exit=slideOutVertically(
                animationSpec=tween(260),
                targetOffsetY={ fullHeight -> fullHeight }
            ) + fadeOut(tween(170)) + scaleOut(tween(220),targetScale=.985f),
            modifier=Modifier.fillMaxSize()
        ) {
            AppDrawer(filtered, search, {search=it}, fg, bg, labels && hideText == "Off",
                drawerTransparency, drawerBlur, {activity.launch(it)}, {drawer=false})
        }
        AnimatedVisibility(visible=controlCenter, enter=androidx.compose.animation.slideInVertically { -it }, exit=androidx.compose.animation.slideOutVertically { -it }) {
            ControlCenter(deviceState, dark, { themeMode=if(it) "dark" else "light"; db.put("theme_mode",themeMode) }, {controlCenter=false})
        }
        AnimatedVisibility(visible=booting, enter=fadeIn(tween(220)), exit=fadeOut(tween(520)), modifier=Modifier.fillMaxSize()) {
            AppleBootScreen()
        }
        AnimatedVisibility(visible=settings, enter=androidx.compose.animation.slideInHorizontally { it }, exit=androidx.compose.animation.slideOutHorizontally { it }) {
            SettingsScreen(
                columns=columns, rows=rows, labels=labels, hideText=hideText, dark=dark,
                homeStyle=homeStyle, minusScreen=minusScreen, searchStyle=searchStyle, swipeDownAction=swipeDownAction,
                animationSpeed=animationSpeed, fillCells=fillCells, lockLayout=lockLayout,
                navigationMode=navigationMode, recentsArrangement=recentsArrangement, highRefreshRate=highRefreshRate,
                themeMode=themeMode, onThemeMode={ themeMode=it; db.put("theme_mode", it) },
                drawerTransparency=drawerTransparency, onDrawerTransparency={ drawerTransparency=it; db.put("drawer_transparency",it.toString()) },
                drawerBlur=drawerBlur, onDrawerBlur={ drawerBlur=it; db.put("drawer_blur",it.toString()) },
                onAddWidget={ widgetProviders=activity.availableHomeWidgets(); widgetPickerOpen=true },
                onChooseWallpaper={ activity.chooseWallpaper() },
                onColumns={columns=it;db.put("grid_columns",it.toString())},
                onRows={rows=it;db.put("grid_rows",it.toString())},
                onLabels={labels=it;db.put("show_labels",it.toString())},
                onHideText={hideText=it;db.put("hide_text",it)},
                onDark={ themeMode=if(it) "dark" else "light"; db.put("theme_mode", themeMode) },
                onHomeStyle={homeStyle=it;db.put("home_style",it)},
                onMinusScreen={minusScreen=it;db.put("minus_screen",it)},
                onSearchStyle={searchStyle=it;db.put("search_style",it)},
                onSwipeDown={swipeDownAction=it;db.put("swipe_down_action",it)},
                onAnimationSpeed={animationSpeed=it;db.put("animation_speed",it)},
                onFillCells={fillCells=it;db.put("fill_cells",it.toString())},
                onLockLayout={lockLayout=it;db.put("lock_layout",it.toString())},
                onNavigationMode={navigationMode=it;db.put("navigation_mode",it)},
                onRecentsArrangement={recentsArrangement=it;db.put("recents_arrangement",it)},
                onHighRefreshRate={highRefreshRate=it;db.put("high_refresh_rate",it.toString()); if(it) activity.requestHighestRefreshRate() else activity.clearPreferredRefreshRate()},
                onClose={settings=false},
                onUninstallLauncher={ pendingUninstallApp=InstalledApp("iOS Style Launcher",context.packageName,context.packageManager.getApplicationIcon(context.packageName)) },
                onDeviceSettings={
                    // Android requires users to approve restricted settings themselves
                    // for sideloaded apps before enabling an Accessibility service.
                    val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    try { context.startActivity(appInfo) }
                    catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }
            )
        }
AnimatedVisibility(visible=openFolderId!=null,enter=fadeIn(tween(150))+scaleIn(spring(dampingRatio=.78f,stiffness=460f),initialScale=.84f),exit=fadeOut(tween(110))+scaleOut(tween(110)),modifier=Modifier.fillMaxSize()) {
            val folder=folders.firstOrNull { it.id==(openFolderId ?: lastOpenedFolderId) }
            if(folder==null) openFolderId=null else {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.22f)).clickable(
                    interactionSource=remember { MutableInteractionSource() },indication=null
                ){openFolderId=null},contentAlignment=Alignment.Center) {
                    Surface(
                        modifier=Modifier.fillMaxWidth(.86f).heightIn(min=270.dp,max=440.dp)
                            .onGloballyPositioned { folderOverlayBounds=it.boundsInRoot() }
                            .clickable(interactionSource=remember { MutableInteractionSource() },indication=null) {},
                        shape=RoundedCornerShape(34.dp),
                        color=Color(0xDDE9EEF4),
                        border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.72f)),
                        shadowElevation=28.dp
                    ) {
                        Column(Modifier.padding(horizontal=20.dp,vertical=18.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                if (renamingFolderId == folder.id) {
                                    OutlinedTextField(
                                        value=renameFolderText,
                                        onValueChange={ renameFolderText=it },
                                        modifier=Modifier.weight(1f),
                                        singleLine=true,
                                        textStyle=androidx.compose.ui.text.TextStyle(fontSize=19.sp,fontWeight=FontWeight.SemiBold),
                                        placeholder={ Text("Group name") }
                                    )
                                    IconButton(onClick={
                                        val newTitle=renameFolderText.trim()
                                        if(newTitle.isNotEmpty()) {
                                            folders=folders.map { item -> if(item.id==folder.id) item.copy(title=newTitle) else item }
                                            persistFolders()
                                        }
                                        renamingFolderId=null
                                    }) { Icon(Icons.Default.Check,null,tint=Color(0xFF30B968)) }
                                } else {
                                    Text(folder.title,color=Color(0xFF20242C),fontSize=21.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                                    IconButton(onClick={ renameFolderText=folder.title; renamingFolderId=folder.id }) {
                                        Icon(Icons.Default.Edit,null,tint=Color(0xFF59616D))
                                    }
                                }
                                IconButton(onClick={openFolderId=null; renamingFolderId=null}) { Icon(Icons.Default.Close,null,tint=Color(0xFF59616D)) }
                            }
                            Spacer(Modifier.height(12.dp))
                            val folderApps=folder.packages.distinct().mapNotNull { pkg -> apps.firstOrNull { it.packageName==pkg } }
                            LazyVerticalGrid(
                                columns=GridCells.Fixed(3),
                                modifier=Modifier.fillMaxWidth().weight(1f,false),
                                horizontalArrangement=Arrangement.spacedBy(8.dp),
                                verticalArrangement=Arrangement.spacedBy(14.dp)
                            ) {
                                items(folderApps.size) { index ->
                                    val app=folderApps[index]
                                    AppIcon(
                                        app=app,labels=true,fg=Color(0xFF242A33),size=54.dp,
                                        onBounds={ key,rect -> folderMemberBounds[key]=rect },
                                        onDragStart={
                                            draggingApp=app
                                            draggingFromDock=false
                                            draggingFromFolder=true
                                            dragSourceFolderId=folder.id
                                            dragPosition=folderMemberBounds[app.packageName]?.center ?: folderOverlayBounds.center
                                            dragOriginBounds=folderMemberBounds[app.packageName] ?: Rect.Zero
                                            dragOriginPage=folder.page
                                            dragOriginSlot=folder.slot
                                            pendingDragPage=folder.page
                                        },
                                        onDrag={ delta -> dragPosition+=delta; updateDragEdge(dragPosition); updateGridDragPreview(dragPosition,"app:"+app.packageName) },
                                        onDragEnd={ finishIconDrag() },
                                        onClick={
                                            openFolderId=null
                                            if(app.packageName=="internal:settings") settings=true else activity.launch(app)
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Drag an app outside to remove it from this folder",color=Color(0xFF66707C),fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        draggingApp?.let { dragged ->
            val bitmap=remember(dragged.packageName) { dragged.icon.toBitmap(144,144).asImageBitmap() }
            val half=with(density){34.dp.toPx()}
            Image(bitmap,contentDescription="Moving ${dragged.label}",
                modifier=Modifier.offset { IntOffset((dragPosition.x-half).toInt(),(dragPosition.y-half).toInt()) }
                    .size(68.dp).clip(RoundedCornerShape(18.dp)).shadow(12.dp,RoundedCornerShape(18.dp)).graphicsLayer { alpha=.96f })
        }
        if (pendingUninstallApp != null) {
            val target=pendingUninstallApp!!
            androidx.compose.ui.window.Dialog(
                onDismissRequest={ pendingUninstallApp=null }
            ) {
                val dialogView=androidx.compose.ui.platform.LocalView.current
                val dialogWindow=(dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                SideEffect {
                    dialogWindow?.let { window ->
                        // Transparent dialog window; do not blur or dim the launcher
                        // backdrop. The translucent glass treatment is confined to Surface.
                        window.setBackgroundDrawableResource(android.R.color.transparent)
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        if (Build.VERSION.SDK_INT >= 31) {
                            window.attributes=window.attributes.apply {
                                blurBehindRadius=0
                                dimAmount=0f
                            }
                        }
                    }
                }
                Surface(
                    modifier=Modifier.fillMaxWidth(.84f),
                    shape=RoundedCornerShape(30.dp),
                    color=Color.Black.copy(alpha=.30f),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.24f)),
                    shadowElevation=26.dp
                ) {
                    Column(Modifier.padding(horizontal=24.dp,vertical=22.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(20.dp,20.dp,20.dp,7.dp)).background(Color(0xFFE9E3FA)),contentAlignment=Alignment.Center) {
                            Icon(Icons.Default.Delete,null,tint=Color(0xFF7554B8),modifier=Modifier.size(27.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Are you sure?",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(7.dp))
                        Text("Uninstall ${target.label} from this device?",color=Color.White.copy(alpha=.76f),fontSize=14.sp,textAlign=TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick={pendingUninstallApp=null},modifier=Modifier.weight(1f)) { Text("No",color=Color.White.copy(alpha=.82f)) }
                            Button(onClick={
                                pendingUninstallApp=null
                                uninstallRequestResumeRevision=activity.appRefreshRevision
                                awaitingUninstallPackage=target.packageName
                                activity.uninstall(target)
                            },modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF7655B8))) {
                                Text("Yes",maxLines=1)
                            }
                        }
                    }
                }
            }
        }
        if (widgetPickerOpen) {
            AlertDialog(
                onDismissRequest={ widgetPickerOpen=false },
                title={ Text("Add widgets") },
                text={
                    if (widgetProviders.isEmpty()) Text("No compatible widgets are currently installed.")
                    else Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
                        widgetProviders.forEach { provider ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    widgetPickerOpen=false
                                    activity.addHomeWidget(page, provider)
                                }.padding(vertical=12.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ) {
                                val icon = try { context.packageManager.getApplicationIcon(provider.provider.packageName) } catch (_: Exception) { null }
                                if (icon != null) Image(icon.toBitmap().asImageBitmap(), null, modifier=Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(provider.loadLabel(context.packageManager).toString(), fontWeight=FontWeight.SemiBold)
                                    Text(provider.provider.packageName, fontSize=10.sp, color=Color.Gray, maxLines=1)
                                    Text(provider.minWidth.toString() + " × " + provider.minHeight + " dp", fontSize=11.sp, color=Color.Gray)
                                }
                                Icon(Icons.Default.Add, null)
                            }
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton={ TextButton(onClick={widgetPickerOpen=false}) { Text("Close") } }
            )
        }
        AnimatedVisibility(visible=draggingApp != null || draggingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID, enter=fadeIn(), exit=fadeOut(), modifier=Modifier.align(Alignment.TopCenter)) {
            Surface(shape=RoundedCornerShape(22.dp), color=Color(0xFFE5484D), shadowElevation=12.dp,
                modifier=Modifier.padding(top=18.dp).size(width=118.dp,height=72.dp)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                    Icon(if (draggingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) Icons.Default.RemoveCircle else Icons.Default.Delete, null, tint=Color.White, modifier=Modifier.size(25.dp))
                    Text(if (draggingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) "Remove" else "Uninstall", color=Color.White, fontSize=11.sp, fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}
