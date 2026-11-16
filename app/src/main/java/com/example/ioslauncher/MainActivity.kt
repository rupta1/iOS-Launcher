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
        window.statusBarColor = android.graphics.Color.rgb(242, 241, 247)
        window.navigationBarColor = android.graphics.Color.rgb(242, 241, 247)
        val needed = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.READ_PHONE_STATE)
        }.filter { ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        setContent { LauncherRoot(db) }
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
    var locked by remember { mutableStateOf(db.get("launcher_unlocked", "false") != "true") }
    var deviceState by remember { mutableStateOf(DeviceStateReader.read(context)) }
    val bg = if (dark) Color(0xFF171923) else Color(0xFFF2F1F7)
    val fg = if (dark) Color.White else Color(0xFF18191F)
    val filtered = apps.filter { it.label.contains(search, true) }

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
        ).pointerInput(drawer, controlCenter, locked) {
            var start = Offset.Zero
            var totalX = 0f
            var totalY = 0f
            detectDragGestures(
                onDragStart = { start = it; totalX = 0f; totalY = 0f },
                onDragEnd = {
                    if (kotlin.math.abs(totalX) > 90 && !drawer && !controlCenter) {
                        page = (page + if (totalX < 0) 1 else -1).coerceIn(0, 2)
                    } else if (totalY < -85 && start.y > size.height - 150) {
                        drawer = true
                    } else if (totalY < -110 && !drawer && !controlCenter) {
                        drawer = true
                    } else if (totalY > 85 && start.y < 130) {
                        controlCenter = true
                    } else if (totalY > 90 && drawer) {
                        drawer = false
                    } else if (totalY < -90 && controlCenter) {
                        controlCenter = false
                    }
                },
                onDrag = { change, drag ->
                    totalX += drag.x; totalY += drag.y
                    change.consume()
                }
            )
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
                val pageApps = apps.chunked(columns*3).getOrNull(targetPage).orEmpty()
                LazyVerticalGrid(
                    columns=GridCells.Fixed(columns),
                    modifier=Modifier.fillMaxSize(),
                    verticalArrangement=Arrangement.spacedBy(18.dp),
                    horizontalArrangement=Arrangement.spacedBy(10.dp),
                    contentPadding=PaddingValues(bottom=12.dp)
                ) {
                    itemsIndexed(pageApps, key={ _, app -> app.packageName }) { index, app ->
                        AppIcon(app, labels, fg, index) {
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
                fg, bg, columns, labels, dark,
                onColumns={columns=it;db.put("grid_columns",it.toString())},
                onLabels={labels=it;db.put("show_labels",it.toString())},
                onDark={dark=it;db.put("dark_mode",it.toString())},
                onClose={settings=false},
                onDeviceSettings={ context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
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
fun SettingsScreen(fg:Color,bg:Color,columns:Int,labels:Boolean,dark:Boolean,onColumns:(Int)->Unit,onLabels:(Boolean)->Unit,onDark:(Boolean)->Unit,onClose:()->Unit,onDeviceSettings:()->Unit) {
    Column(Modifier.fillMaxSize().background(bg).padding(WindowInsets.statusBars.asPaddingValues()).padding(18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Settings",fontSize=28.sp,fontWeight=FontWeight.Bold,color=fg,modifier=Modifier.weight(1f))
            IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=fg)}
        }
        Text("Customize your Home Screen",color=fg.copy(alpha=.65f),fontSize=13.sp)
        Spacer(Modifier.height(18.dp))
        Surface(shape=RoundedCornerShape(20.dp),color=Color.White,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Home Screen",fontWeight=FontWeight.Bold,color=Color.DarkGray)
                Text("Icon columns: $columns",color=Color.DarkGray)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(3,4,5).forEach { n -> FilterChip(selected=columns==n,onClick={onColumns(n)},label={Text("$n")}) } }
                Row(verticalAlignment=Alignment.CenterVertically) { Text("Show icon labels",Modifier.weight(1f),color=Color.DarkGray);Switch(labels,onLabels) }
                Row(verticalAlignment=Alignment.CenterVertically) { Text("Dark appearance",Modifier.weight(1f),color=Color.DarkGray);Switch(dark,onDark) }
            }
        }
        Spacer(Modifier.height(12.dp))
        listOf("Wi-Fi, mobile network & Bluetooth","Display & brightness","Battery","Notifications","Privacy & security","Android system settings").forEach { item ->
            Surface(onClick=onDeviceSettings,shape=RoundedCornerShape(15.dp),color=Color.White,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(item,color=Color.DarkGray,modifier=Modifier.weight(1f),fontSize=14.sp);Icon(Icons.Default.ChevronRight,null,tint=Color.Gray)
                }
            }
        }
    }
}
