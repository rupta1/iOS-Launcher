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
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
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
                else listOf(Color(0xFFDFE9F6), Color(0xFFF7F5FA), Color(0xFFDCE6F4))
            )
        ).pointerInput(drawer, controlCenter) {
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
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), color = fg.copy(alpha=.72f), fontSize=13.sp)
                    Text("Good afternoon", color = fg, fontSize=28.sp, fontWeight=FontWeight.Bold)
                }
                Surface(shape=CircleShape, color=Color.White.copy(alpha=.5f), modifier=Modifier.size(42.dp).clickable { controlCenter=true }) {
                    Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Tune, null, tint=fg) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Your apps", color=fg, fontWeight=FontWeight.SemiBold, fontSize=16.sp)
                Text("Page ${page+1} of 3", color=fg.copy(alpha=.55f), fontSize=12.sp)
            }
            Spacer(Modifier.height(14.dp))
            val pageApps = apps.chunked(columns*4).getOrNull(page).orEmpty()
            LazyVerticalGrid(
                columns=GridCells.Fixed(columns),
                modifier=Modifier.weight(1f),
                verticalArrangement=Arrangement.spacedBy(20.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),
                contentPadding=PaddingValues(bottom=16.dp)
            ) {
                items(pageApps, key={it.packageName}) { app ->
                    AppIcon(app, labels, fg) {
                        if (app.packageName == "internal:settings") settings = true else activity.launch(app)
                    }
                }
                item {
                    if (pageApps.isEmpty()) Text("Install apps to see them here", color=fg.copy(alpha=.6f), fontSize=12.sp)
                }
            }
            Surface(shape=RoundedCornerShape(28.dp), color=Color.White.copy(alpha=if (dark) 0f else .48f), modifier=Modifier.fillMaxWidth().padding(bottom=18.dp)) {
                Row(Modifier.padding(horizontal=10.dp, vertical=10.dp), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically) {
                    val pinned = listOf("com.google.android.dialer","com.android.dialer","com.google.android.apps.messaging","com.android.messaging","com.android.chrome","com.google.android.googlequicksearchbox")
                    pinned.mapNotNull { p -> apps.firstOrNull { it.packageName==p } }.distinctBy{it.packageName}.take(4).forEach { AppIcon(it, false, fg, 52.dp) { activity.launch(it) } }
                    DockButton(Icons.Default.Apps, "Apps", fg) { drawer=true }
                    DockButton(Icons.Default.Settings, "Settings", fg) { settings=true }
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
    }
}

@Composable
fun AppIcon(app: InstalledApp, labels: Boolean, fg: Color, size: Dp = 58.dp, onClick: () -> Unit) {
    val bitmap = remember(app.packageName) { app.icon.toBitmap(144,144).asImageBitmap() }
    Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.clickable { onClick() }) {
        Image(bitmap, null, Modifier.size(size).clip(RoundedCornerShape(17.dp)).shadow(3.dp, RoundedCornerShape(17.dp)))
        if (labels) {
            Spacer(Modifier.height(5.dp))
            Text(app.label, color=fg, fontSize=10.sp, maxLines=1, textAlign=TextAlign.Center)
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
            items(apps, key={it.packageName}) { AppIcon(it, labels, fg, 54.dp) { onLaunch(it) } }
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
