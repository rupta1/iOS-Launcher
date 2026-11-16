package com.example.ioslauncher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.gestures.detectDragGestures
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
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LauncherDb(this)
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
    fun requestHighestRefreshRate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val mode = display?.supportedModes?.maxByOrNull { it.refreshRate }
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
    var apps by remember { mutableStateOf(activity.loadApps()) }
    var drawer by remember { mutableStateOf(false) }
    var controlCenter by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var columns by remember { mutableIntStateOf(db.get("grid_columns", "4").toIntOrNull() ?: 4) }
    var labels by remember { mutableStateOf(db.get("show_labels", "true") == "true") }
    var dark by remember { mutableStateOf(db.get("dark_mode", "false") == "true") }
    var page by remember { mutableIntStateOf(0) }
    var rows by remember { mutableIntStateOf(db.get("grid_rows", "7").toIntOrNull() ?: 7) }
    var navigationMode by remember { mutableStateOf(db.get("navigation_mode", "gestures")) }
    var animationSpeed by remember { mutableStateOf(db.get("animation_speed", "Fast")) }
    var homeStyle by remember { mutableStateOf(db.get("home_style", "With App drawer")) }
    var minusScreen by remember { mutableStateOf(db.get("minus_screen", "Google Discover")) }
    var searchStyle by remember { mutableStateOf(db.get("search_style", "Compact search")) }
    var swipeDownAction by remember { mutableStateOf(db.get("swipe_down_action", "App finder")) }
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
    val showLabels = labels && hideText == "Off"

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
        ).pointerInput(drawer, controlCenter, locked, navigationMode, homeStyle, swipeDownAction, page) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
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
                    if (kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f && !drawer && !controlCenter && !locked) {
                        page = (page + if (dx < 0) 1 else -1).coerceIn(0, 2)
                        change.consume()
                        finished = true
                    } else if (kotlin.math.abs(dy) > threshold && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) {
                        if (dy < 0 && down.position.y > size.height * .78f && !drawer && !controlCenter && !locked && homeStyle == "With App drawer") {
                            drawer = true; change.consume(); finished = true
                        } else if (dy > 0 && down.position.y < size.height * .18f && !locked && swipeDownAction == "App finder") {
                            drawer = true; search = ""; change.consume(); finished = true
                        } else if (dy > 0 && drawer) {
                            drawer = false; change.consume(); finished = true
                        } else if (dy < 0 && controlCenter) {
                            controlCenter = false; change.consume(); finished = true
                        } else if (dy > 0 && down.position.y < size.height * .18f && !locked) {
                            controlCenter = true; change.consume(); finished = true
                        } else if (down.position.y > size.height * .18f && down.position.y < size.height * .78f) {
                            finished = true
                        }
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
                    (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { if (targetState > initialState) it else -it } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { if (targetState > initialState) -it else it } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)))
                },
                label = "home-pages"
            ) { targetPage ->
                val pageApps = apps.chunked(columns*rows).getOrNull(targetPage).orEmpty()
                LazyVerticalGrid(
                    columns=GridCells.Fixed(columns),
                    modifier=Modifier.fillMaxSize(),
                    verticalArrangement=Arrangement.spacedBy(18.dp),
                    horizontalArrangement=Arrangement.spacedBy(10.dp),
                    contentPadding=PaddingValues(bottom=12.dp)
                ) {
                    itemsIndexed(pageApps, key={ _, app -> app.packageName }) { index, app ->
                        AppIcon(app, labels && hideText == "Off", fg, index, animationSpeed) {
                            if (app.packageName == "internal:settings") settings = true else activity.launch(app)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp), horizontalArrangement=Arrangement.Center) {
                repeat(3) { index ->
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
                    dock.forEach { item -> AppIcon(item,false,fg,0,52.dp) { activity.launch(item) } }
                    DockButton(Icons.Default.Message,"Messages",fg) {
                        apps.firstOrNull { it.label.contains("Message",true) || it.label.contains("SMS",true) }?.let { activity.launch(it) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom=4.dp), contentAlignment=Alignment.Center) {
                Box(Modifier.width(108.dp).height(5.dp).clip(CircleShape).background(fg.copy(alpha=.5f)).clickable { drawer=true })
            }
        }

        AnimatedVisibility(visible=drawer, enter=androidx.compose.animation.fadeIn(), exit=androidx.compose.animation.fadeOut()) {
            AppDrawer(filtered, search, {search=it}, fg, bg, labels, {activity.launch(it)}, {drawer=false})
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
                onDeviceSettings={context.startActivity(Intent(Settings.ACTION_SETTINGS))}
            )
        }
        AnimatedVisibility(visible=locked, enter=fadeIn(tween(280)), exit=fadeOut(tween(300))) {
            LauncherLockScreen {
                locked = false
                db.put("launcher_unlocked", "true")
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

@Composable
fun AppIcon(app:InstalledApp, labels:Boolean, fg:Color, index:Int=0, size:Dp=62.dp, onClick:()->Unit) {
    var shown by remember(app.packageName) { mutableStateOf(false) }
    LaunchedEffect(app.packageName) { kotlinx.coroutines.delay(index.coerceAtMost(10)*28L); shown=true }
    val scale by animateFloatAsState(if(shown) 1f else .72f, androidx.compose.animation.core.tween(360), label="icon-scale")
    val alpha by animateFloatAsState(if(shown) 1f else 0f, androidx.compose.animation.core.tween(300), label="icon-alpha")
    val iconRes=iconResourceForLabel(app.label)
    Column(horizontalAlignment=Alignment.CenterHorizontally,
        modifier=Modifier.graphicsLayer { scaleX=scale; scaleY=scale; this.alpha=alpha }) {
        Surface(onClick=onClick, modifier=Modifier.size(size).shadow(5.dp,RoundedCornerShape(21.dp)),
            shape=RoundedCornerShape(21.dp), color=Color.White) {
            if(iconRes!=0) {
                Image(painterResource(iconRes),null,Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)))
            } else {
                Image(app.icon.toBitmap(192,192).asImageBitmap(),null,
                    Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)).padding(1.dp))
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
        LazyVerticalGrid(columns=GridCells.Fixed(4), verticalArrangement=Arrangement.spacedBy(20.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(apps, key={it.packageName}) { AppIcon(it, labels, fg, 0, 54.dp) { onLaunch(it) } }
        }
    }
}

@Composable
fun ControlCenter(state: DeviceState, dark: Boolean, onDark:(Boolean)->Unit, onClose:()->Unit) {
    val context= LocalContext.current
    val bg=Color(0xFFE8E9EF)
    Column(Modifier.fillMaxSize().background(bg.copy(alpha=.98f)).padding(WindowInsets.statusBars.asPaddingValues()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text("Control Center", fontSize=25.sp, fontWeight=FontWeight.Bold, color=Color(0xFF17191F), modifier=Modifier.weight(1f))
            IconButton(onClick=onClose) { Icon(Icons.Default.Close,null) }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.1f), verticalArrangement=Arrangement.spacedBy(9.dp)) {
                ControlTile("✈","Airplane mode","Open system setting") { context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                ControlTile("▥","${state.transport}","${if(state.online) "Connected" else "Offline"}") { context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
                ControlTile("ᛒ","Bluetooth",state.bluetooth) { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
            Column(Modifier.weight(.9f), verticalArrangement=Arrangement.spacedBy(9.dp)) {
                ControlTile("♫","Not Playing","Media controls") { }
                ControlTile("☾","Focus","Do Not Disturb settings") { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            SliderTile("☼","Brightness", Modifier.weight(1f)) { value ->
                val activity = context as? Activity
                activity?.window?.attributes = activity.window.attributes.apply { screenBrightness = value }
            }
            SliderTile("♫","Volume", Modifier.weight(1f)) { value ->
                val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).toInt(), 0)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SmallTile("▣","Rotation", Modifier.weight(1f)) { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            SmallTile("▰","Low Power", Modifier.weight(1f)) { context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
            SmallTile("◐","Dark Mode", Modifier.weight(1f)) { onDark(!dark) }
        }
        Spacer(Modifier.height(16.dp))
        Text("DEVICE STATUS", color=Color.Gray, fontSize=11.sp, fontWeight=FontWeight.Bold)
        Surface(shape=RoundedCornerShape(18.dp), color=Color.White, modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Internet: ${if(state.online) "Online" else "Offline"}", color=Color.DarkGray, fontSize=13.sp)
                Text("Network: ${state.transport}", color=Color.DarkGray, fontSize=13.sp)
                Text("Wi-Fi SSID: ${state.wifiSsid}", color=Color.DarkGray, fontSize=13.sp)
                Text("Bluetooth: ${state.bluetooth}", color=Color.DarkGray, fontSize=13.sp)
                Text("Cellular: ${state.cellularType}", color=Color.DarkGray, fontSize=13.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Some controls open Android system panels; Android restricts direct toggling by third-party launchers.", color=Color.Gray, fontSize=10.sp)
    }
}

@Composable
fun ControlTile(icon:String,title:String,subtitle:String,onClick:()->Unit) {
    Surface(onClick=onClick, shape=RoundedCornerShape(20.dp), color=Color.White, modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF1685F8)), contentAlignment=Alignment.Center) { Text(icon,color=Color.White,fontSize=18.sp) }
            Spacer(Modifier.width(8.dp))
            Column { Text(title,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color.DarkGray);Text(subtitle,fontSize=9.sp,color=Color.Gray) }
        }
    }
}
@Composable
fun SliderTile(icon:String,title:String,modifier:Modifier=Modifier,onChange:(Float)->Unit) {
    var value by remember { mutableFloatStateOf(.65f) }
    Surface(modifier, shape=RoundedCornerShape(20.dp), color=Color.White) {
        Column(Modifier.padding(12.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            Text(icon,fontSize=20.sp,color=Color.DarkGray)
            Slider(value,{value=it;onChange(it)})
            Text(title,fontSize=10.sp,color=Color.Gray)
        }
    }
}
@Composable
fun SmallTile(icon:String,title:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(18.dp),color=Color.White) {
        Column(Modifier.padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(icon,fontSize=22.sp,color=Color.DarkGray);Text(title,fontSize=10.sp,color=Color.Gray)
        }
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
                SettingChoice("Swipe down on the Home screen",swipeDownAction,fg,muted) { choose("Swipe down on the Home screen",swipeDownAction,listOf("App finder","Notification shade","Control Center","None"),onSwipeDown) }
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

