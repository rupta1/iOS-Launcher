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
fun AppDrawer(apps: List<InstalledApp>, search: String, onSearch: (String)->Unit, fg: Color, bg: Color, labels: Boolean,
    transparency:Float, blur:Float, onLaunch: (InstalledApp)->Unit, onClose:()->Unit) {
    val context=LocalContext.current
    val density=LocalDensity.current
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var selectedBounds by remember { mutableStateOf(Rect.Zero) }
    val gridState=rememberLazyListState()
    var selectedCategory by remember { mutableStateOf("All") }
    val categories=listOf("All","Communication","Entertainment","Photography","Shopping","Tools","Other")
    fun categoryOf(app:InstalledApp):String {
        val value=(app.label+" "+app.packageName).lowercase(Locale.getDefault())
        return when {
            listOf("phone","dialer","message","sms","whatsapp","telegram","signal","gmail","mail","outlook","contacts").any { it in value } -> "Communication"
            listOf("youtube","netflix","music","spotify","video","prime","game","hotstar","mx player").any { it in value } -> "Entertainment"
            listOf("camera","photo","gallery","snapseed","lightroom","instagram").any { it in value } -> "Photography"
            listOf("amazon","flipkart","shopping","store","meesho","myntra").any { it in value } -> "Shopping"
            listOf("setting","calculator","clock","file","browser","chrome","maps","calendar","notes").any { it in value } -> "Tools"
            else -> "Other"
        }
    }
    val visibleApps=remember(apps,search,selectedCategory) {
        val query=search.trim().lowercase(Locale.getDefault())
        apps.filter { app ->
            (query.isEmpty() || app.label.lowercase(Locale.getDefault()).contains(query) || app.packageName.lowercase(Locale.getDefault()).contains(query)) &&
                (query.isNotEmpty() || selectedCategory=="All" || categoryOf(app)==selectedCategory)
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF101724).copy(alpha=transparency))) {
        Column(
            Modifier.fillMaxSize()
                .background(Color.Transparent)
                .padding(horizontal=18.dp)
                .padding(top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+12.dp)
        ) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("App Library",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
                    Text("${visibleApps.size} apps",color=Color.White.copy(alpha=.66f),fontSize=12.sp)
                }
                IconButton(onClick=onClose) {
                    Icon(Icons.Default.Close,null,tint=Color.White,modifier=Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier=Modifier.fillMaxWidth().height(52.dp),
                shape=RoundedCornerShape(17.dp),
                color=Color.White.copy(alpha=.13f),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.18f))
            ) {
                Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Search,null,tint=Color.White.copy(alpha=.72f))
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value=search,onValueChange={ onSearch(it); selectedApp=null },
                        modifier=Modifier.weight(1f),
                        singleLine=true,
                        textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=15.sp),
                        cursorBrush=SolidColor(Color.White),
                        decorationBox={ inner ->
                            Box {
                                if(search.isEmpty()) Text("Search apps",color=Color.White.copy(alpha=.58f),fontSize=15.sp)
                                inner()
                            }
                        }
                    )
                    if(search.isNotEmpty()) {
                        IconButton(onClick={onSearch("");selectedApp=null},modifier=Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close,null,tint=Color.White.copy(alpha=.75f),modifier=Modifier.size(17.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val selected=selectedCategory==category
                    Surface(onClick={selectedCategory=category;selectedApp=null},shape=RoundedCornerShape(18.dp),
                        color=if(selected) Color.White else Color.White.copy(alpha=.12f),
                        border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=if(selected) .9f else .16f))) {
                        Text(category,color=if(selected) Color(0xFF202633) else Color.White,fontSize=12.sp,
                            fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Medium,
                            modifier=Modifier.padding(horizontal=13.dp,vertical=8.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if(visibleApps.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Default.SearchOff,null,tint=Color.White.copy(alpha=.72f),modifier=Modifier.size(34.dp))
                        Text("No apps found",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text("Try another search or category",color=Color.White.copy(alpha=.62f),fontSize=12.sp)
                    }
                }
            } else LazyVerticalGrid(
                columns=GridCells.Fixed(4),
                state=rememberLazyGridState(),
                modifier=Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(22.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp),
                contentPadding=PaddingValues(top=4.dp,bottom=30.dp)
            ) {
                items(items=visibleApps,key={ app -> app.packageName },contentType={ "app-icon" }) { app ->
                    DrawerAppIcon(
                        app=app,labels=true,
                        onBounds={ rect -> if(selectedApp?.packageName==app.packageName && selectedBounds!=rect) selectedBounds=rect },
                        onLongClick={ if(!app.packageName.startsWith("internal:")) selectedApp=app },
                        onClick={ selectedApp=null; onLaunch(app) }
                    )
                }
            }
        }
        val chosen=selectedApp
        if(chosen!=null && selectedBounds.width>0f && selectedBounds.height>0f) {
            val offsetX=(selectedBounds.center.x-with(density){58.dp.toPx()}).toInt().coerceAtLeast(8)
            val offsetY=(selectedBounds.bottom+with(density){7.dp.toPx()}).toInt()
            Popup(
                alignment=Alignment.TopStart,
                offset=IntOffset(offsetX,offsetY),
                onDismissRequest={selectedApp=null}
            ) {
                Surface(
                    modifier=Modifier.clickable {
                        selectedApp=null
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data=android.net.Uri.parse("package:${chosen.packageName}")
                                }
                            )
                        } catch (_:Exception) { }
                    },
                    shape=RoundedCornerShape(20.dp,20.dp,20.dp,7.dp),
                    color=Color(0xF4F7F8FC),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.9f)),
                    shadowElevation=18.dp
                ) {
                    Row(
                        Modifier.padding(horizontal=15.dp,vertical=11.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info,null,tint=Color(0xFF247DE3),modifier=Modifier.size(18.dp))
                        Text("App info",color=Color(0xFF202631),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerAppIcon(
    app:InstalledApp, labels:Boolean, modifier:Modifier=Modifier,
    onBounds:(Rect)->Unit, onLongClick:()->Unit, onClick:()->Unit
) {
    val iconRes=remember(app.packageName,app.label) { iconResourceForLabel(app.label,app.packageName) }
    val fallback=remember(app.packageName) { app.icon.toBitmap(96,96).asImageBitmap() }
    Column(
        modifier=modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .combinedClickable(
                interactionSource=remember { MutableInteractionSource() },
                indication=null,
                onClick=onClick,
                onLongClick=onLongClick
            ),
        horizontalAlignment=Alignment.CenterHorizontally
    ) {
        Surface(modifier=Modifier.size(54.dp),shape=RoundedCornerShape(16.dp),color=Color.White) {
            if(iconRes!=0) Image(painterResource(iconRes),null,Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
            else Image(fallback,null,Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
        }
        if(labels) {
            Spacer(Modifier.height(6.dp))
            Text(app.label,color=Color.White,fontSize=11.sp,maxLines=1,textAlign=TextAlign.Center)
        }
    }
}
