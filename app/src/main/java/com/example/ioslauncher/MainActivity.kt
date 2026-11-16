package com.example.ioslauncher

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.isSystemInDarkTheme
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class InstalledApp(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : ComponentActivity() {
    private lateinit var db: LauncherDb
    lateinit var widgetHost: AppWidgetHost
        private set
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var pendingWidgetPage: Int = 0
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val widgetPickRequest = 9101
    private val widgetConfigureRequest = 9102
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LauncherDb(this)
        widgetHost = AppWidgetHost(this, 0x10A1)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        applyPreferredRefreshRate()
        val needed = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.READ_PHONE_STATE)
        }.filter { ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        setContent { LauncherRoot(db) }
    }

    private fun applyPreferredRefreshRate() {
        if (db.get("high_refresh_rate", "true") == "true") requestHighestRefreshRate()
    }
    fun addHomeWidget(page: Int) {
        pendingWidgetPage = page.coerceAtLeast(0)
        pendingWidgetId = widgetHost.allocateAppWidgetId()
        val pick = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_HOST_ID, 0x10A1)
        }
        try { startActivityForResult(pick, widgetPickRequest) }
        catch (_: Exception) { widgetHost.deleteAppWidgetId(pendingWidgetId); pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID }
    }

    @Deprecated("Deprecated in Android, retained for AppWidgetHost picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != widgetPickRequest && requestCode != widgetConfigureRequest) return
        if (resultCode != Activity.RESULT_OK) {
            if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            return
        }
        if (requestCode == widgetPickRequest) {
            val selectedId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            pendingWidgetId = selectedId
            val info = widgetManager.getAppWidgetInfo(selectedId)
            if (info?.configure != null) {
                val configure = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = info.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, selectedId)
                }
                try { startActivityForResult(configure, widgetConfigureRequest) }
                catch (_: Exception) { persistPickedWidget() }
            } else persistPickedWidget()
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
    override fun onStop() { try { widgetHost.stopListening() } catch (_: Exception) { }; super.onStop() }

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
                    InstalledApp("Settings", "internal:settings", android.graphics.drawable.GradientDrawable().apply { setColor(android.graphics.Color.rgb(125, 130, 145)); cornerRadius=28f })
                )
            )
            .sortedWith(compareBy<InstalledApp> {
                val preferred = listOf("Phone", "Messages", "Safari", "Chrome", "Calendar", "Photos", "Camera", "Weather", "Clock", "Settings", "Notes", "Maps", "Music", "Files", "Health")
                val position = preferred.indexOfFirst { token -> it.label.contains(token, true) }
                if (position < 0) 1000 else position
            }.thenBy { it.label.lowercase(Locale.getDefault()) })
    }

    fun uninstall(app: InstalledApp) {
        if (app.packageName.startsWith("internal:")) return
        try {
            startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${app.packageName}")))
        } catch (_: Exception) { }
    }

    fun launch(app: InstalledApp) {
        try {
            when (app.packageName) {
                "internal:browser" -> startActivity(Intent(this, BrowserActivity::class.java))
                "internal:notes" -> startActivity(Intent(this, NotesActivity::class.java))
                "internal:settings" -> startActivity(Intent(Settings.ACTION_SETTINGS))
                else -> {
                    val i = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
                    db.log("app_launch", app.packageName)
                    startActivity(i)
                }
            }
        } catch (_: Exception) { }
    }
}

@Composable
fun LauncherRoot(db: LauncherDb) {
    val context = LocalContext.current
    val activity = context as MainActivity
    var apps by remember {
        val loaded = activity.loadApps()
        val saved = db.get("app_order").split("|").filter { it.isNotBlank() }
        mutableStateOf(if (saved.isEmpty()) loaded else loaded.sortedBy { app ->
            val index = saved.indexOf(app.packageName)
            if (index < 0) Int.MAX_VALUE else index
        })
    }
    var draggingApp by remember { mutableStateOf<InstalledApp?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val appBounds = remember { mutableStateMapOf<String, Rect>() }
    val density = LocalDensity.current
    val uninstallDropZoneHeight = with(density) { 112.dp.toPx() }
    var drawer by remember { mutableStateOf(false) }
    var controlCenter by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var columns by remember { mutableIntStateOf(db.get("grid_columns", "4").toIntOrNull() ?: 4) }
    var labels by remember { mutableStateOf(db.get("show_labels", "true") == "true") }
    var dark by remember { mutableStateOf(db.get("dark_mode", "false") == "true") }
    var page by remember { mutableIntStateOf(0) }
    var rows by remember { mutableIntStateOf(db.get("grid_rows", "7").toIntOrNull() ?: 7) }
    var navigationMode by remember { mutableStateOf(db.get("navigation_mode", "buttons")) }
    var animationSpeed by remember { mutableStateOf(db.get("animation_speed", "Fast")) }
    var homeStyle by remember { mutableStateOf(db.get("home_style", "With App drawer")) }
    var minusScreen by remember { mutableStateOf(db.get("minus_screen", "Google Discover")) }
    var searchStyle by remember { mutableStateOf(db.get("search_style", "Compact search")) }
    var swipeDownAction by remember {
        val savedSwipeAction = db.get("swipe_down_action", "Control Center")
        mutableStateOf(if (savedSwipeAction == "App finder") "Control Center" else savedSwipeAction)
    }
    var doubleTapLock by remember { mutableStateOf(db.get("double_tap_lock", "false") == "true") }
    var fillCells by remember { mutableStateOf(db.get("fill_cells", "true") == "true") }
    var lockLayout by remember { mutableStateOf(db.get("lock_layout", "false") == "true") }
    var hideText by remember { mutableStateOf(db.get("hide_text", "Off")) }
    var recentsArrangement by remember { mutableStateOf(db.get("recents_arrangement", "Vertically")) }
    var highRefreshRate by remember { mutableStateOf(db.get("high_refresh_rate", "true") == "true") }
    var locked by remember { mutableStateOf(db.get("launcher_unlocked", "false") != "true") }
    var deviceState by remember { mutableStateOf(DeviceStateReader.read(context)) }
    val bg = if (dark) Color(0xFF171923) else Color(0xFFF2F1F7)
    val fg = if (dark) Color.White else Color(0xFF18191F)
    val filtered = apps.filter { it.label.contains(search, true) }
    fun finishIconDrag() {
        val dragged = draggingApp ?: return
        if (dragPosition.y < uninstallDropZoneHeight && !dragged.packageName.startsWith("internal:")) {
            activity.uninstall(dragged)
            draggingApp = null
            return
        }
        val target = appBounds.entries.firstOrNull { (key, rect) ->
            key != dragged.packageName && rect.contains(dragPosition)
        }?.key
        if (target != null) {
            val list = apps.toMutableList()
            val from = list.indexOfFirst { it.packageName == dragged.packageName }
            val to = list.indexOfFirst { it.packageName == target }
            if (from >= 0 && to >= 0 && from != to) {
                val item = list.removeAt(from)
                list.add(to, item)
                apps = list
                db.put("app_order", list.joinToString("|") { it.packageName })
            }
        }
        draggingApp = null
    }
    val showLabels = labels && hideText == "Off"
    val pageCount = ((apps.size + columns * rows - 1) / (columns * rows)).coerceAtLeast(1)
    SideEffect {
        activity.window.statusBarColor = if (settings) android.graphics.Color.BLACK else android.graphics.Color.rgb(37, 38, 94)
        activity.window.navigationBarColor = if (settings) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
        val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
    }
    LaunchedEffect(navigationMode, settings) {
        val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (navigationMode == "gestures" && !settings) {
            bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        } else {
            bars.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }
    LaunchedEffect(pageCount) { if (page >= pageCount) page = pageCount - 1 }

    LaunchedEffect(Unit) {
        while (true) {
            deviceState = DeviceStateReader.read(context)
            kotlinx.coroutines.delay(4000)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (dark) listOf(Color(0xFF22283A), Color(0xFF10131D))
                else listOf(Color(0xFF25265E), Color(0xFF62528D), Color(0xFFB36F8B), Color(0xFFE4A17C))
            )
        ).pointerInput(drawer, controlCenter, locked, settings, navigationMode, homeStyle, swipeDownAction, minusScreen, page) {
            if (!drawer && !controlCenter && !settings && !locked) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (appBounds.values.none { it.contains(down.position) }) {
                    var dx = 0f
                    var dy = 0f
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
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
        }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).graphicsLayer {
            renderEffect = if (controlCenter && android.os.Build.VERSION.SDK_INT >= 31) BlurEffect(20f, 20f) else null
        }) {
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(greeting(), color = fg.copy(alpha=.72f), fontSize=14.sp)
                    Text(SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()), color = fg, fontSize=24.sp, fontWeight=FontWeight.SemiBold)
                }
                Surface(shape=CircleShape, color=Color.White.copy(alpha=.5f), modifier=Modifier.size(42.dp).clickable { controlCenter=true }) {
                    Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Tune, null, tint=fg) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().padding(top=18.dp, bottom=12.dp), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                HomeWidget("WEATHER", "24°", "Mostly Sunny", Icons.Default.WbSunny, Modifier.weight(1f), Color(0xFFFFB648))
                HomeWidget("TODAY", SimpleDateFormat("d", Locale.getDefault()).format(Date()),
                    SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault()),
                    Icons.Default.CalendarMonth, Modifier.weight(1f), Color(0xFFEF6375))
            }
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (androidx.compose.animation.slideInHorizontally(spring(dampingRatio=.86f,stiffness=when(animationSpeed) { "Slow" -> 260f; "Normal" -> 430f; else -> 720f })) { if (targetState > initialState) it else -it } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(110))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(spring(dampingRatio=.9f,stiffness=when(animationSpeed) { "Slow" -> 300f; "Normal" -> 500f; else -> 800f })) { if (targetState > initialState) -it else it } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)))
                },
                label = "home-pages"
            ) { targetPage ->
                val pageApps = if (targetPage < 0) emptyList() else apps.chunked(columns*rows).getOrNull(targetPage).orEmpty()
                if (targetPage < 0 && minusScreen == "Google Discover") {
                    val googleApp = apps.firstOrNull { it.packageName == "com.google.android.googlequicksearchbox" }
                    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("Google Discover",color=fg,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                        Text("Open the Google app to view your Discover feed.",color=fg.copy(alpha=.7f),fontSize=14.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick={googleApp?.let { activity.launch(it) }}) { Text("Open Google") }
                    }
                } else LazyVerticalGrid(
                    columns=GridCells.Fixed(columns),
                    modifier=Modifier.fillMaxSize()
                        .pointerInput(doubleTapLock) {
                            detectTapGestures(onDoubleTap={ if(doubleTapLock && !locked) locked=true })
                        }
                        .pointerInput(page, pageCount) {
                            var horizontalDistance = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag={ change, amount ->
                                    horizontalDistance += amount
                                    change.consume()
                                },
                                onDragEnd={
                                    if (kotlin.math.abs(horizontalDistance) > 70f) {
                                        page = (page + if (horizontalDistance < 0f) 1 else -1).coerceIn(0, pageCount - 1)
                                    }
                                    horizontalDistance = 0f
                                },
                                onDragCancel={ horizontalDistance = 0f }
                            )
                        },
                    verticalArrangement=Arrangement.spacedBy(18.dp),
                    horizontalArrangement=Arrangement.spacedBy(10.dp),
                    contentPadding=PaddingValues(bottom=12.dp)
                ) {
                    itemsIndexed(pageApps, key={ _, app -> app.packageName }) { index, app ->
                        val isDragging = draggingApp?.packageName == app.packageName
                        val originalCenter = appBounds[app.packageName]?.center ?: dragPosition
                        val translation = if (isDragging) dragPosition - originalCenter else Offset.Zero
                        AppIcon(
                            app=app, labels=labels && hideText == "Off", fg=fg, index=index, animationSpeed=animationSpeed,
                            dragTranslation=translation, isDragging=isDragging,
                            onBounds={ key, rect -> if (draggingApp?.packageName != key) appBounds[key] = rect },
                            onDragStart={ if (!lockLayout) { draggingApp=app; dragPosition=appBounds[app.packageName]?.center ?: Offset.Zero } },
                            onDrag={ delta -> dragPosition += delta },
                            onDragEnd={ finishIconDrag() },
                            onClick={ if (app.packageName == "internal:settings") settings=true else activity.launch(app) }
                        )
                    }
                    if (fillCells) {
                        items((columns * rows - pageApps.size).coerceAtLeast(0)) {
                            Spacer(Modifier.size(62.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp), horizontalArrangement=Arrangement.Center) {
                repeat(pageCount) { index ->
                    Box(Modifier.padding(horizontal=4.dp).size(if(page==index) 9.dp else 7.dp)
                        .clip(CircleShape).background(if(page==index) Color(0xFF1685F8) else fg.copy(alpha=.24f)))
                }
            }
            Surface(shape=RoundedCornerShape(30.dp), color=Color.White.copy(alpha=if (dark) .16f else .52f),
                modifier=Modifier.fillMaxWidth().padding(bottom=16.dp).border(1.dp,Color.White.copy(alpha=.45f),RoundedCornerShape(30.dp))) {
                Row(Modifier.padding(horizontal=12.dp, vertical=12.dp), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically) {
                    val dock = listOf(
                        apps.firstOrNull { it.label.contains("Phone",true) || it.label.contains("Dialer",true) },
                        apps.firstOrNull { it.label.contains("Chrome",true) || it.label.contains("Browser",true) },
                        apps.firstOrNull { it.label.contains("Music",true) }
                    ).filterNotNull().distinctBy { it.packageName }.take(3)
                    dock.forEach { item -> AppIcon(app=item,labels=false,fg=fg,index=0,animationSpeed="Fast",size=52.dp,onClick={activity.launch(item)}) }
                    DockButton(Icons.Default.Message,"Messages",fg) {
                        apps.firstOrNull { it.label.contains("Message",true) || it.label.contains("SMS",true) }?.let { activity.launch(it) }
                    }
                    DockButton(Icons.Default.Settings,"Settings",fg) { settings=true }
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom=4.dp), contentAlignment=Alignment.Center) {
                Box(Modifier.width(108.dp).height(5.dp).clip(CircleShape).background(fg.copy(alpha=.5f)).clickable { if(homeStyle=="With App drawer") drawer=true })
            }
        }

        BackHandler(enabled=true) {
            when {
                controlCenter -> controlCenter=false
                drawer -> drawer=false
                settings -> settings=false
                draggingApp != null -> draggingApp=null
                else -> Unit
            }
        }
        AnimatedVisibility(visible=drawer, enter=androidx.compose.animation.fadeIn(), exit=androidx.compose.animation.fadeOut()) {
            AppDrawer(filtered, search, {search=it}, fg, bg, labels && hideText == "Off", {activity.launch(it)}, {drawer=false})
        }
        AnimatedVisibility(visible=controlCenter, enter=androidx.compose.animation.slideInVertically { -it }, exit=androidx.compose.animation.slideOutVertically { -it }) {
            ControlCenter(deviceState, dark, { dark=it; db.put("dark_mode",it.toString()) }, {controlCenter=false})
        }
        AnimatedVisibility(visible=settings, enter=androidx.compose.animation.slideInHorizontally { it }, exit=androidx.compose.animation.slideOutHorizontally { it }) {
            SettingsScreen(
                columns=columns, rows=rows, labels=labels, hideText=hideText, dark=dark,
                homeStyle=homeStyle, minusScreen=minusScreen, searchStyle=searchStyle, swipeDownAction=swipeDownAction,
                animationSpeed=animationSpeed, doubleTapLock=doubleTapLock, fillCells=fillCells, lockLayout=lockLayout,
                navigationMode=navigationMode, recentsArrangement=recentsArrangement, highRefreshRate=highRefreshRate,
                onColumns={columns=it;db.put("grid_columns",it.toString())},
                onRows={rows=it;db.put("grid_rows",it.toString())},
                onLabels={labels=it;db.put("show_labels",it.toString())},
                onHideText={hideText=it;db.put("hide_text",it)},
                onDark={dark=it;db.put("dark_mode",it.toString())},
                onHomeStyle={homeStyle=it;db.put("home_style",it)},
                onMinusScreen={minusScreen=it;db.put("minus_screen",it)},
                onSearchStyle={searchStyle=it;db.put("search_style",it)},
                onSwipeDown={swipeDownAction=it;db.put("swipe_down_action",it)},
                onAnimationSpeed={animationSpeed=it;db.put("animation_speed",it)},
                onDoubleTapLock={doubleTapLock=it;db.put("double_tap_lock",it.toString())},
                onFillCells={fillCells=it;db.put("fill_cells",it.toString())},
                onLockLayout={lockLayout=it;db.put("lock_layout",it.toString())},
                onNavigationMode={navigationMode=it;db.put("navigation_mode",it)},
                onRecentsArrangement={recentsArrangement=it;db.put("recents_arrangement",it)},
                onHighRefreshRate={highRefreshRate=it;db.put("high_refresh_rate",it.toString()); if(it) activity.requestHighestRefreshRate() else activity.clearPreferredRefreshRate()},
                onClose={settings=false},
                onDeviceSettings={
                    try { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }
            )
        }
        AnimatedVisibility(visible=locked, enter=fadeIn(tween(280)), exit=fadeOut(tween(300))) {
            LauncherLockScreen {
                locked = false
                db.put("launcher_unlocked", "true")
            }
        }
        AnimatedVisibility(visible=draggingApp != null, enter=fadeIn(), exit=fadeOut(), modifier=Modifier.align(Alignment.TopCenter)) {
            Surface(shape=RoundedCornerShape(22.dp), color=Color(0xFFE5484D), shadowElevation=12.dp,
                modifier=Modifier.padding(top=18.dp).size(width=118.dp,height=72.dp)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                    Icon(Icons.Default.Delete, null, tint=Color.White, modifier=Modifier.size(25.dp))
                    Text("Uninstall", color=Color.White, fontSize=11.sp, fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun LauncherLockScreen(onUnlock:()->Unit) {
    var clock by remember { mutableStateOf(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())) }
    var date by remember { mutableStateOf(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())) }
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }
    Box(Modifier.fillMaxSize()
        .background(Brush.linearGradient(listOf(Color(0xFF1E285A), Color(0xFF65518D), Color(0xFFB66D82), Color(0xFFDE9B79))))
        .pointerInput(Unit) {
            detectDragGestures(onDrag={ change, amount -> drag += amount.y; change.consume() },
                onDragEnd={ if (drag < -65f) onUnlock(); drag=0f })
        }) {
        Column(Modifier.align(Alignment.TopCenter).padding(top=42.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.width(118.dp).height(34.dp).clip(CircleShape).background(Color.Black))
            Spacer(Modifier.height(80.dp))
            Text(date, color=Color.White, fontSize=20.sp)
            Text(clock, color=Color.White, fontSize=76.sp, fontWeight=FontWeight.ExtraLight)
            Spacer(Modifier.height(24.dp))
            Text("☀  24°", color=Color.White, fontSize=22.sp)
            Text("Mostly Sunny", color=Color.White.copy(alpha=.9f), fontSize=15.sp)
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=22.dp).padding(bottom=105.dp),
            shape=RoundedCornerShape(24.dp), color=Color.White.copy(alpha=.8f)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Message, null, tint=Color(0xFF2AC46D), modifier=Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Messages", color=Color(0xFF25252B), fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f))
                    Text("now", color=Color.Gray, fontSize=12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Your launcher is ready. Swipe up to explore.", color=Color(0xFF35353D), fontSize=14.sp)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom=26.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Swipe up to unlock", color=Color.White.copy(alpha=.8f), fontSize=13.sp)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(110.dp).height(5.dp).clip(CircleShape).background(Color.White))
        }
    }
}

private fun greeting():String = when(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun HomeWidget(kicker:String, value:String, subtitle:String, icon:ImageVector, modifier:Modifier, accent:Color) {
    Surface(modifier.height(136.dp), shape=RoundedCornerShape(25.dp), color=Color.White.copy(alpha=.84f)) {
        Column(Modifier.padding(15.dp), verticalArrangement=Arrangement.SpaceBetween) {
            Text(kicker, color=Color(0xFF777781), fontSize=10.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(value, color=accent, fontSize=33.sp, fontWeight=FontWeight.Light, modifier=Modifier.weight(1f))
                Icon(icon, null, tint=accent, modifier=Modifier.size(28.dp))
            }
            Text(subtitle, color=Color(0xFF55555E), fontSize=12.sp, maxLines=1)
        }
    }
}

private fun iconResourceForLabel(label:String):Int {
    val n=label.lowercase(Locale.getDefault())
    return when {
        "calendar" in n -> R.drawable.ios18_calendar
        "camera" in n -> R.drawable.ios18_camera
        "message" in n || "sms" in n -> R.drawable.ios18_messages
        "note" in n || "keep" in n -> R.drawable.ios18_notes
        "safari" in n || "browser" in n -> R.drawable.ios18_safari
        "chrome" in n -> R.drawable.ios18_chrome
        "setting" in n -> R.drawable.ios18_settings
        "weather" in n -> R.drawable.ios18_weather
        "photo" in n || "gallery" in n -> R.drawable.ios18_photos
        "file" in n -> R.drawable.ios18_files
        "music" in n || "spotify" in n -> R.drawable.ios18_music
        "phone" in n || "dialer" in n -> R.drawable.ios18_phone
        "health" in n -> R.drawable.ios18_health
        "map" in n -> R.drawable.ios18_maps
        "amazon" in n -> R.drawable.ios18_amazon
        "gmail" in n || "mail" in n -> R.drawable.ios18_gmail
        "calculator" in n -> R.drawable.ios18_calculator
        "youtube" in n -> R.drawable.ios18_youtube
        "whatsapp" in n -> R.drawable.ios18_whatsapp
        "app store" in n || "play store" in n -> R.drawable.ios18_appstore
        else -> 0
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app:InstalledApp, labels:Boolean, fg:Color, index:Int=0, animationSpeed:String="Fast", size:Dp=62.dp,
    dragTranslation:Offset=Offset.Zero, isDragging:Boolean=false,
    onBounds:(String,Rect)->Unit={ _, _ -> }, onDragStart:()->Unit={}, onDrag:(Offset)->Unit={ _ -> },
    onDragEnd:()->Unit={}, onClick:()->Unit
) {
    var shown by remember(app.packageName) { mutableStateOf(false) }
    LaunchedEffect(app.packageName) { shown=true }
    val duration = when(animationSpeed) { "Slow" -> 300; "Normal" -> 210; else -> 140 }
    val scale by animateFloatAsState(if(shown) 1f else .96f, spring(stiffness=androidx.compose.animation.core.Spring.StiffnessMediumLow), label="icon-scale")
    val alpha by animateFloatAsState(if(shown) 1f else 0f, androidx.compose.animation.core.tween(duration), label="icon-alpha")
    val iconRes=iconResourceForLabel(app.label)
    val fallbackBitmap = remember(app.packageName) { app.icon.toBitmap(192,192).asImageBitmap() }
    Column(
        horizontalAlignment=Alignment.CenterHorizontally,
        modifier=Modifier.graphicsLayer {
            translationX=dragTranslation.x
            translationY=dragTranslation.y
            scaleX=scale * if(isDragging) 1.10f else 1f
            scaleY=scale * if(isDragging) 1.10f else 1f
            this.alpha=if(isDragging) .88f else alpha
        }
            .onGloballyPositioned { onBounds(app.packageName,it.boundsInRoot()) }
            .pointerInput(app.packageName) {
                detectDragGesturesAfterLongPress(
                    onDragStart={ onDragStart() },
                    onDrag={ change, amount -> change.consume(); onDrag(amount) },
                    onDragEnd=onDragEnd,
                    onDragCancel=onDragEnd
                )
            }
            .clickable(
                interactionSource=remember { MutableInteractionSource() },
                indication=null,
                onClick=onClick
            )
    ) {
        Surface(modifier=Modifier.size(size).shadow(5.dp,RoundedCornerShape(21.dp)),
            shape=RoundedCornerShape(21.dp), color=Color.White) {
            if(iconRes!=0) {
                Image(painterResource(iconRes),null,Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)))
            } else {
                Image(fallbackBitmap,null,Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)).padding(1.dp))
            }
        }
        if(labels) {
            Spacer(Modifier.height(6.dp))
            Text(app.label,color=fg,fontSize=11.sp,maxLines=1,textAlign=TextAlign.Center)
        }
    }
}
@Composable
fun DockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, fg: Color, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(horizontal=8.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(icon, null, tint=fg, modifier=Modifier.size(28.dp))
        Text(label, color=fg, fontSize=9.sp)
    }
}

@Composable
fun AppDrawer(apps: List<InstalledApp>, search: String, onSearch: (String)->Unit, fg: Color, bg: Color, labels: Boolean, onLaunch: (InstalledApp)->Unit, onClose:()->Unit) {
    Column(Modifier.fillMaxSize().background(bg.copy(alpha=.98f)).padding(horizontal=18.dp).padding(top=32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text("App Library", color=fg, fontSize=27.sp, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
            IconButton(onClick=onClose) { Icon(Icons.Default.Close, null, tint=fg) }
        }
        OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), placeholder={Text("Search apps")}, leadingIcon={Icon(Icons.Default.Search,null)}, singleLine=true)
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(columns=GridCells.Fixed(4), modifier=Modifier.weight(1f).fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(20.dp), horizontalArrangement=Arrangement.spacedBy(8.dp), contentPadding=PaddingValues(bottom=24.dp)) {
            items(apps, key={it.packageName}) { AppIcon(app=it,labels=labels,fg=fg,index=0,animationSpeed="Fast",size=54.dp,onClick={onLaunch(it)}) }
        }
    }
}

@Composable
fun ControlCenter(state: DeviceState, dark: Boolean, onDark:(Boolean)->Unit, onClose:()->Unit) {
    val context=LocalContext.current
    val audio=context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    val maxVolume=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()/maxVolume) }
    var brightness by remember { mutableFloatStateOf(.72f) }
    var focusOn by remember { mutableStateOf(false) }
    var rotationLocked by remember { mutableStateOf(false) }
    var airplaneOn by remember { mutableStateOf(false) }
    var wifiOn by remember { mutableStateOf(state.transport=="Wi-Fi") }
    var bluetoothOn by remember { mutableStateOf(state.bluetooth=="On") }
    val white=Color.White
    val panel=Color(0xFF5B6477).copy(alpha=.48f)
    val panelBorder=Color.White.copy(alpha=.16f)
    val blue=Color(0xFF168AF4)
    fun open(action:Intent) { try { context.startActivity(action) } catch (_:Exception) { } }
    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF101724).copy(alpha=.30f))
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal=18.dp, vertical=10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp, vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={ open(Intent(Settings.ACTION_SETTINGS)) }) {
                Icon(Icons.Default.Add,null,tint=white,modifier=Modifier.size(25.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick={ open(Intent(Settings.ACTION_SETTINGS)) }) {
                Icon(Icons.Default.PowerSettingsNew,null,tint=white,modifier=Modifier.size(25.dp))
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()) {
            Surface(
                modifier=Modifier.weight(1.08f).height(196.dp),
                shape=RoundedCornerShape(30.dp),color=panel,border=androidx.compose.foundation.BorderStroke(1.dp,panelBorder)
            ) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.weight(1f)) {
                        IosConnectivityButton(Icons.Default.AirplanemodeActive,"Airplane",airplaneOn,Modifier.weight(1f),blue) {
                            open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                        }
                        IosConnectivityButton(Icons.Default.Wifi,"Wi-Fi",wifiOn,Modifier.weight(1f),blue) {
                            open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.weight(1f)) {
                        IosConnectivityButton(Icons.Default.Bluetooth,"Bluetooth",bluetoothOn,Modifier.weight(1f),blue) {
                            open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }
                        IosConnectivityButton(Icons.Default.SignalCellularAlt,"Mobile",state.cellularType!="Off",Modifier.weight(1f),blue) {
                            open(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
                        }
                    }
                }
            }
            Surface(
                modifier=Modifier.weight(.92f).height(196.dp),
                shape=RoundedCornerShape(30.dp),color=panel,border=androidx.compose.foundation.BorderStroke(1.dp,panelBorder)
            ) {
                Column(Modifier.fillMaxSize().padding(15.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha=.12f)))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Airplay,null,tint=white.copy(alpha=.85f),modifier=Modifier.size(23.dp))
                    }
                    Text("Not Playing",color=white,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.SkipPrevious,null,tint=white.copy(alpha=.55f),modifier=Modifier.size(24.dp))
                        Icon(Icons.Default.PlayArrow,null,tint=white,modifier=Modifier.size(34.dp))
                        Icon(Icons.Default.SkipNext,null,tint=white.copy(alpha=.55f),modifier=Modifier.size(24.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            IosRoundAction(Icons.Default.ScreenRotation,"Lock",rotationLocked,Modifier.weight(1f)) { rotationLocked=!rotationLocked; open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            IosRoundAction(Icons.Default.ScreenShare,"Screen Mirroring",false,Modifier.weight(1f)) { open(Intent(Settings.ACTION_CAST_SETTINGS)) }
            Column(Modifier.weight(.82f).height(174.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                IosVerticalSlider(brightness,{ brightness=it
                    val window=(context as? Activity)?.window
                    window?.attributes=window.attributes.apply { screenBrightness=it.coerceIn(.02f,1f) }
                },Icons.Default.WbSunny)
            }
            Column(Modifier.weight(.82f).height(174.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                IosVerticalSlider(volume,{ volume=it; audio.setStreamVolume(AudioManager.STREAM_MUSIC,(it*maxVolume).toInt().coerceIn(0,maxVolume),0) },Icons.Default.VolumeUp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick={focusOn=!focusOn;open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))},
            shape=RoundedCornerShape(28.dp),color=panel,border=androidx.compose.foundation.BorderStroke(1.dp,panelBorder),
            modifier=Modifier.fillMaxWidth().height(66.dp)
        ) {
            Row(Modifier.padding(horizontal=17.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(blue),contentAlignment=Alignment.Center) {
                    Icon(Icons.Default.NightlightRound,null,tint=white,modifier=Modifier.size(23.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Focus",color=white,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore,null,tint=white.copy(alpha=.8f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            IosUtilityButton(Icons.Default.FlashlightOn,"Flashlight",Modifier.weight(1f)) { open(Intent(Settings.ACTION_SETTINGS)) }
            IosUtilityButton(Icons.Default.Timer,"Timer",Modifier.weight(1f)) { open(Intent(android.provider.AlarmClock.ACTION_SET_TIMER)) }
            IosUtilityButton(Icons.Default.Calculate,"Calculator",Modifier.weight(1f)) {
                val launch=context.packageManager.getLaunchIntentForPackage("com.google.android.calculator")
                if(launch!=null) open(launch) else open(Intent(Settings.ACTION_SETTINGS))
            }
            IosUtilityButton(Icons.Default.PhotoCamera,"Camera",Modifier.weight(1f)) { open(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            IosUtilityButton(Icons.Default.QrCodeScanner,"Code Scanner",Modifier.weight(1f)) { open(Intent(Settings.ACTION_SETTINGS)) }
            Spacer(Modifier.weight(3f))
        }
        Spacer(Modifier.height(20.dp))
        Text("Swipe up to close",color=white.copy(alpha=.58f),fontSize=10.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun IosConnectivityButton(icon:ImageVector,label:String,active:Boolean,modifier:Modifier=Modifier,blue:Color=Color(0xFF168AF4),onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier.fillMaxHeight(),shape=CircleShape,color=if(active) blue else Color.White.copy(alpha=.15f)) {
        Box(contentAlignment=Alignment.Center) { Icon(icon,null,tint=Color.White,modifier=Modifier.size(25.dp)) }
    }
}
@Composable
fun IosRoundAction(icon:ImageVector,label:String,active:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Surface(onClick=onClick,shape=CircleShape,color=if(active) Color(0xFFDAF3E6) else Color.White.copy(alpha=.18f),
            border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.14f)),modifier=Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(contentAlignment=Alignment.Center) { Icon(icon,null,tint=if(active) Color(0xFFE56A75) else Color.White,modifier=Modifier.size(27.dp)) }
        }
        Spacer(Modifier.height(5.dp))
        Text(label,color=Color.White.copy(alpha=.75f),fontSize=9.sp,maxLines=1)
    }
}
@Composable
fun IosVerticalSlider(value:Float,onValue:(Float)->Unit,icon:ImageVector) {
    var sliderHeight by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha=.18f))
        .pointerInput(Unit) {
            detectTapGestures(onTap={ offset -> onValue((1f-offset.y/size.height).coerceIn(0f,1f)) })
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart={ offset -> onValue((1f-offset.y/size.height).coerceIn(0f,1f)) },
                onDrag={ change,_ -> change.consume(); onValue((1f-change.position.y/size.height).coerceIn(0f,1f)) }
            )
        },
        contentAlignment=Alignment.BottomCenter
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(.02f,1f)).clip(RoundedCornerShape(28.dp)).background(Color.White))
        Icon(icon,null,tint=if(value>.55f) Color(0xFFFFC84A) else Color(0xFF25BDEB),modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp).size(22.dp))
    }
}
@Composable
fun IosUtilityButton(icon:ImageVector,label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(onClick=onClick,shape=CircleShape,color=Color.White.copy(alpha=.18f),
            border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.18f)),modifier=Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(contentAlignment=Alignment.Center) { Icon(icon,null,tint=Color.White,modifier=Modifier.size(27.dp)) }
        }
        Spacer(Modifier.height(5.dp))
        Text(label,color=Color.White.copy(alpha=.78f),fontSize=9.sp,maxLines=1)
    }
}

@Composable
fun SettingsScreen(
    columns:Int, rows:Int, labels:Boolean, hideText:String, dark:Boolean,
    homeStyle:String, minusScreen:String, searchStyle:String, swipeDownAction:String,
    animationSpeed:String, doubleTapLock:Boolean, fillCells:Boolean, lockLayout:Boolean,
    navigationMode:String, recentsArrangement:String, highRefreshRate:Boolean,
    onColumns:(Int)->Unit, onRows:(Int)->Unit, onLabels:(Boolean)->Unit, onHideText:(String)->Unit,
    onDark:(Boolean)->Unit, onHomeStyle:(String)->Unit, onMinusScreen:(String)->Unit,
    onSearchStyle:(String)->Unit, onSwipeDown:(String)->Unit, onAnimationSpeed:(String)->Unit,
    onDoubleTapLock:(Boolean)->Unit, onFillCells:(Boolean)->Unit, onLockLayout:(Boolean)->Unit,
    onNavigationMode:(String)->Unit, onRecentsArrangement:(String)->Unit, onHighRefreshRate:(Boolean)->Unit,
    onClose:()->Unit, onDeviceSettings:()->Unit
) {
    var choiceTitle by remember { mutableStateOf<String?>(null) }
    var choiceValue by remember { mutableStateOf("") }
    var choiceOptions by remember { mutableStateOf(emptyList<String>()) }
    var choiceAction by remember { mutableStateOf<(String)->Unit>({}) }
    val card=Color(0xFF242424)
    val fg=Color(0xFFF5F5F7)
    val muted=Color(0xFF99999F)
    fun choose(title:String,value:String,options:List<String>,action:(String)->Unit) {
        choiceTitle=title; choiceValue=value; choiceOptions=options; choiceAction=action
    }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(WindowInsets.statusBars.asPaddingValues()).padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=4.dp), verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onClose) { Icon(Icons.Default.ArrowBack,null,tint=fg) }
        }
        Text("Home screen",color=fg,fontSize=29.sp,fontWeight=FontWeight.Normal,modifier=Modifier.padding(top=18.dp,bottom=24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(18.dp)) {
            SettingsGroup("Home screen settings",card,muted) {
                SettingChoice("Home screen",homeStyle,fg,muted) { choose("Home screen",homeStyle,listOf("With App drawer","Without App drawer"),onHomeStyle) }
                SettingChoice("-1 screen",minusScreen,fg,muted) { choose("-1 screen",minusScreen,listOf("Google Discover","None"),onMinusScreen) }
                SettingChoice("Home screen search",searchStyle,fg,muted) { choose("Home screen search",searchStyle,listOf("Compact search","Full screen search"),onSearchStyle) }
                SettingChoice("Swipe down on the Home screen",swipeDownAction,fg,muted) { choose("Swipe down on the Home screen",swipeDownAction,listOf("Control Center","None"),onSwipeDown) }
                SettingChoice("Animation speed",animationSpeed,fg,muted) { choose("Animation speed",animationSpeed,listOf("Fast","Normal","Slow"),onAnimationSpeed) }
                SettingSwitch("Double tap an empty spot on the Home screen to lock",doubleTapLock,fg,onDoubleTapLock)
            }
            SettingsGroup("Home screen layout",card,muted) {
                SettingSwitch("Fill cells of uninstalled apps",fillCells,fg,onFillCells)
                SettingSwitch("Lock Home screen layout",lockLayout,fg,onLockLayout)
                SettingChoice("Don't show text",hideText,fg,muted) { choose("Don't show text",hideText,listOf("Off","App names","All labels"),onHideText) }
                SettingChoice("Home screen layout","${columns}×${rows}",fg,muted) {
                    choose("Home screen layout","${columns}×${rows}",listOf("4×5","4×6","4×7","5×5","5×6","5×7","3×6"),{ v ->
                        val parts=v.split("×"); onColumns(parts[0].toInt()); onRows(parts[1].toInt())
                    })
                }
            }
            SettingsGroup("Other",card,muted) {
                SettingChoice("System navigation",if(navigationMode=="buttons") "Buttons" else "Gestures",fg,muted) {
                    choose("System navigation",if(navigationMode=="buttons") "Buttons" else "Gestures",listOf("Buttons","Gestures"),{ value ->
                        onNavigationMode(value.lowercase())
                        onDeviceSettings()
                    })
                }
                SettingChoice("Arrange items in Recents",recentsArrangement,fg,muted) {
                    choose("Arrange items in Recents",recentsArrangement,listOf("Vertically","Horizontally"),onRecentsArrangement)
                }
                SettingSwitch("Prefer highest supported refresh rate",highRefreshRate,fg,onHighRefreshRate)
            }
            Surface(shape=RoundedCornerShape(18.dp),color=card,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("About system navigation",color=fg,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text("Buttons or gesture navigation is controlled by Android / MIUI / HyperOS. Choose a mode above, then finish the change in your phone's system settings. The launcher uses edge swipes for its own actions when Gestures is selected.",color=muted,fontSize=12.sp)
                    Text("Recents layout is owned by the system launcher. This preference is saved here but cannot rearrange Android's system Recents screen.",color=muted,fontSize=12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if(choiceTitle!=null) {
        AlertDialog(
            onDismissRequest={choiceTitle=null},
            title={Text(choiceTitle!!)},
            text={
                Column {
                    choiceOptions.forEach { option ->
                        Row(Modifier.fillMaxWidth().clickable {
                            choiceAction(option); choiceTitle=null
                        }.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(selected=option.equals(choiceValue,true),onClick={
                                choiceAction(option); choiceTitle=null
                            })
                            Spacer(Modifier.width(10.dp))
                            Text(option)
                        }
                    }
                }
            },
            confirmButton={ TextButton(onClick={choiceTitle=null}) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsGroup(title:String,card:Color,muted:Color,content:@Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,color=Color(0xFF85859A),fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=16.dp,bottom=2.dp))
        Surface(shape=RoundedCornerShape(20.dp),color=card,modifier=Modifier.fillMaxWidth()) {
            Column(content=content)
        }
    }
}

@Composable
private fun SettingChoice(title:String,value:String,fg:Color,muted:Color,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=18.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=fg,fontSize=14.sp,modifier=Modifier.weight(1f))
        Text(value,color=muted,fontSize=13.sp,maxLines=1)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight,null,tint=muted,modifier=Modifier.size(18.dp))
    }
}

@Composable
private fun SettingSwitch(title:String,checked:Boolean,fg:Color,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal=18.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=fg,fontSize=14.sp,modifier=Modifier.weight(1f))
        Switch(checked=checked,onCheckedChange=onChange)
    }
}

