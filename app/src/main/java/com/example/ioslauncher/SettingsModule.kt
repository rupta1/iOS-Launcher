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

@Composable
fun SettingsScreen(
    columns:Int, rows:Int, labels:Boolean, hideText:String, dark:Boolean,
    homeStyle:String, minusScreen:String, searchStyle:String, swipeDownAction:String,
    animationSpeed:String, fillCells:Boolean, lockLayout:Boolean,
    navigationMode:String, recentsArrangement:String, highRefreshRate:Boolean,
    themeMode:String, onThemeMode:(String)->Unit,
    drawerTransparency:Float, onDrawerTransparency:(Float)->Unit, drawerBlur:Float, onDrawerBlur:(Float)->Unit,
    onUninstallLauncher:()->Unit, onAddWidget:()->Unit, onChooseWallpaper:()->Unit,
    onColumns:(Int)->Unit, onRows:(Int)->Unit, onLabels:(Boolean)->Unit, onHideText:(String)->Unit,
    onDark:(Boolean)->Unit, onHomeStyle:(String)->Unit, onMinusScreen:(String)->Unit,
    onSearchStyle:(String)->Unit, onSwipeDown:(String)->Unit, onAnimationSpeed:(String)->Unit,
    onFillCells:(Boolean)->Unit, onLockLayout:(Boolean)->Unit,
    onNavigationMode:(String)->Unit, onRecentsArrangement:(String)->Unit, onHighRefreshRate:(Boolean)->Unit,
    onClose:()->Unit, onDeviceSettings:()->Unit
) {
    var choiceTitle by remember { mutableStateOf<String?>(null) }
    var choiceValue by remember { mutableStateOf("") }
    var choiceOptions by remember { mutableStateOf(emptyList<String>()) }
    var choiceAction by remember { mutableStateOf<(String)->Unit>({}) }
    val card=if(dark) Color(0xFF242424) else Color.White
    val fg=if(dark) Color(0xFFF5F5F7) else Color(0xFF17171B)
    val muted=if(dark) Color(0xFF99999F) else Color(0xFF777780)
    fun choose(title:String,value:String,options:List<String>,action:(String)->Unit) {
        choiceTitle=title; choiceValue=value; choiceOptions=options; choiceAction=action
    }
    Column(Modifier.fillMaxSize().background(if(dark) Color.Black else Color(0xFFF3F2F7)).padding(WindowInsets.statusBars.asPaddingValues()).padding(horizontal=18.dp)) {
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
            }
            SettingsGroup("Personalization",card,muted) {
                SettingChoice("Wallpaper","Choose wallpaper",fg,muted) { onChooseWallpaper() }
                Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("App Library transparency",color=fg,fontSize=14.sp,modifier=Modifier.weight(1f))
                        Text("${(drawerTransparency*100).toInt()}%",color=muted,fontSize=13.sp)
                    }
                    Slider(value=drawerTransparency,onValueChange=onDrawerTransparency,valueRange=0f..0.80f)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("App Library blur",color=fg,fontSize=14.sp,modifier=Modifier.weight(1f))
                        Text("${drawerBlur.toInt()} px",color=muted,fontSize=13.sp)
                    }
                    Slider(value=drawerBlur,onValueChange=onDrawerBlur,valueRange=0f..32f)
                }
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
                SettingChoice("Arrange items in Recents",recentsArrangement,fg,muted) {
                    choose("Arrange items in Recents",recentsArrangement,listOf("Vertically","Horizontally"),onRecentsArrangement)
                }
                SettingChoice("Appearance",themeMode.replaceFirstChar { it.uppercase() },fg,muted) {
                    choose("Appearance",themeMode,listOf("system","light","dark"),onThemeMode)
                }
                SettingChoice("Widgets","Add system or third-party widgets",fg,muted,onAddWidget)
                SettingSwitch("Prefer highest supported refresh rate",highRefreshRate,fg,onHighRefreshRate)
                SettingChoice("Uninstall launcher","Remove from device",fg,muted,onUninstallLauncher)
            }
            Surface(shape=RoundedCornerShape(18.dp),color=card,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("Navigation bar",color=fg,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text("Three-button navigation remains enabled. The navigation bar automatically hides on the Home screen to provide more space; swipe up from the bottom edge to reveal the Back, Home and Recents buttons temporarily.",color=muted,fontSize=12.sp)
                    Text("The system navigation mode is unchanged. The navigation buttons remain available and reappear temporarily when you swipe from the bottom edge.",color=muted,fontSize=12.sp)
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
