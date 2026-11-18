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
fun ControlCenter(state: DeviceState, dark: Boolean, onDark:(Boolean)->Unit, onClose:()->Unit) {
    val context=LocalContext.current
    val audio=context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    val maxVolume=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()/maxVolume) }
    var brightness by remember { mutableFloatStateOf(.72f) }
    var torchOn by remember { mutableStateOf(false) }
    fun open(action:Intent) { try { context.startActivity(action) } catch (_:Exception) { } }
    val cameraManager = remember { context.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager }
    fun toggleTorch() {
        val cameraId = try { cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } } catch (_:Exception) { null }
        if(cameraId != null) try { cameraManager.setTorchMode(cameraId,!torchOn); torchOn=!torchOn } catch (_:Exception) { open(Intent(Settings.ACTION_SETTINGS)) }
    }
    val torchPermission = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) toggleTorch()
    }
    val wifiOn = state.transport=="Wi-Fi"
    val bluetoothOn = state.bluetooth=="On"
    val airplaneOn = try { android.provider.Settings.Global.getInt(context.contentResolver,android.provider.Settings.Global.AIRPLANE_MODE_ON,0)==1 } catch (_:Exception) { false }
    val white=Color.White
    val panel=Color(0xFF5B6477).copy(alpha=.48f)
    val panelBorder=Color.White.copy(alpha=.16f)
    val blue=Color(0xFF168AF4)
    fun mediaKey(code:Int) {
        val eventTime=android.os.SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(eventTime,eventTime,android.view.KeyEvent.ACTION_DOWN,code,0))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(eventTime,eventTime,android.view.KeyEvent.ACTION_UP,code,0))
    }
    Box(Modifier.fillMaxSize()) {
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
                        IconButton(onClick={mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)}) { Icon(Icons.Default.SkipPrevious,null,tint=white,modifier=Modifier.size(24.dp)) }
                        IconButton(onClick={mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)}) { Icon(Icons.Default.PlayArrow,null,tint=white,modifier=Modifier.size(34.dp)) }
                        IconButton(onClick={mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)}) { Icon(Icons.Default.SkipNext,null,tint=white,modifier=Modifier.size(24.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            IosRoundAction(Icons.Default.ScreenRotation,"Rotation",false,Modifier.weight(1f)) { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
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
            onClick={open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))},
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
            IosUtilityButton(if(torchOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,"Flashlight",Modifier.weight(1f)) {
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) toggleTorch()
                else torchPermission.launch(Manifest.permission.CAMERA)
            }
            IosUtilityButton(Icons.Default.Timer,"Timer",Modifier.weight(1f)) { open(Intent(android.provider.AlarmClock.ACTION_SET_TIMER)) }
            IosUtilityButton(Icons.Default.Calculate,"Calculator",Modifier.weight(1f)) {
                val launch=context.packageManager.getLaunchIntentForPackage("com.google.android.calculator")
                if(launch!=null) open(launch) else open(Intent(Settings.ACTION_SETTINGS))
            }
            IosUtilityButton(Icons.Default.PhotoCamera,"Camera",Modifier.weight(1f)) { open(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            IosUtilityButton(Icons.Default.QrCodeScanner,"Code Scanner",Modifier.weight(1f)) {
                try { context.startActivity(Intent("com.google.zxing.client.android.SCAN")) }
                catch (_:Exception) { open(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)) }
            }
            Spacer(Modifier.weight(3f))
        }
        Spacer(Modifier.height(20.dp))
    }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(54.dp).pointerInput(onClose) {
                var upward=0f
                detectDragGestures(
                    onDragStart={ upward=0f },
                    onDrag={ change,amount ->
                        change.consume()
                        if(amount.y<0f) upward+=amount.y
                        if(upward < -52f) { onClose(); upward=0f }
                    },
                    onDragEnd={ upward=0f },
                    onDragCancel={ upward=0f }
                )
            },
            contentAlignment=Alignment.Center
        ) {
            Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(white.copy(alpha=.58f)))
        }
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
