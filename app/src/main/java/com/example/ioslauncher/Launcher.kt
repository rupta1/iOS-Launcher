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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.LocalTextStyle
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


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    fun gridBoundsFor(pageIndex:Int):Rect {
        // Never use another page's root bounds during AnimatedContent. Its outgoing
        // page can be translated while the incoming page is still being measured.
        return homeGridBoundsByPage[pageIndex]?.takeIf { it.width>0f && it.height>0f }
            ?: Rect.Zero
    }
    var draggingApp by remember { mutableStateOf<InstalledApp?>(null) }
    var widgetSearch by remember { mutableStateOf("") }
    var widgetPickerDrag by remember { mutableStateOf<android.appwidget.AppWidgetProviderInfo?>(null) }
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
    var showWeatherWidget by remember { mutableStateOf(false) }
    var showTodayWidget by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        db.put("show_weather_widget", "false")
        db.put("show_today_widget", "false")
    }
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
    var edgePagingArmed by remember { mutableStateOf(true) }
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
    var gridDialog by remember { mutableStateOf(false) }
    var batchMoveDialog by remember { mutableStateOf(false) }
    var batchFolderDialog by remember { mutableStateOf(false) }
    var selectedEditApps by remember { mutableStateOf(setOf<String>()) }
    var selectedPlacementApp by remember { mutableStateOf<InstalledApp?>(null) }
    var placementPage by remember { mutableIntStateOf(0) }
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
    // The workspace grid itself is measured by BoxWithConstraints; use the configured row count
    // directly rather than estimating it from physical display height.
    val effectiveRows = rows.coerceAtLeast(1)
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
    val wallpaperPrimary = remember { try { WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb() ?: android.graphics.Color.BLACK } catch (_:Exception) { android.graphics.Color.BLACK } }
    val wallpaperLuminance = (0.299*android.graphics.Color.red(wallpaperPrimary)+0.587*android.graphics.Color.green(wallpaperPrimary)+0.114*android.graphics.Color.blue(wallpaperPrimary))/255.0
    val wallpaperText = if (wallpaperLuminance < 0.52) Color.White else Color(0xFF111111)
    val fg = wallpaperText
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
    ): Int {
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

        // The destination can shift left when empty pages are compacted. Return
        // its new index so drag completion never selects the pre-compaction page.
        val normalizedDestination = pageMap[destinationPage.coerceAtLeast(0)]
            ?: (pageMap.values.maxOrNull() ?: 0)
        page = normalizedDestination
        pendingDragPage = normalizedDestination
        db.put("app_pages", appPageAssignments.entries.joinToString(";") { it.key + ":" + it.value })
        db.put("home_widgets", homeWidgets.joinToString(",") { it.first.toString() + ":" + it.second })
        db.put("home_folders", encodeHomeFolders(folders))
        db.put("weather_widget_page", weatherWidgetPage.toString())
        db.put("today_widget_page", todayWidgetPage.toString())
        db.put("extra_home_pages", "0")
        return normalizedDestination
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
            val safeGrid=gridBoundsFor(destinationPage)
            if(safeGrid.width<=0f||safeGrid.height<=0f||!safeGrid.contains(widgetDragPosition)){android.widget.Toast.makeText(context,"Drop inside the home screen area",android.widget.Toast.LENGTH_SHORT).show();draggingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID;dragPreviewSlots=emptyMap();dragEdgeDirection=0;pendingDragPage=page;return}
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
        val normalizedPage = normalizeHomePages(
            movingWidgetId=if(id >= 0 && homeWidgets.any { it.first == id }) id else AppWidgetManager.INVALID_APPWIDGET_ID,
            destinationPage=destinationPage
        )
        persistWidgetPositions()
        draggingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID
        dragPreviewSlots=emptyMap()
        dragEdgeDirection=0
        page=normalizedPage
        pendingDragPage=normalizedPage
    }
    // Keep one additional, empty workspace page after the populated pages.
    // It uses the same real InstalledApp models and the existing drag/drop renderer,
    // so apps can be moved onto it and tested without introducing mock launcher icons.
    val pageCount = maxOf(
        pageAppSlices.size + 1,
        (homeWidgets.maxOfOrNull { it.second } ?: 0) + 1,
        (folders.maxOfOrNull { it.page } ?: 0) + 1,
        (if (showWeatherWidget) weatherWidgetPage else 0) + 1,
        (if (showTodayWidget) todayWidgetPage else 0) + 1,
        extraHomePages + 1,
        1
    )
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
        // Allow one trailing blank page, but do not treat that blank page as
        // occupied when calculating the limit, preventing endless page creation.
        val occupiedLast=lastHomePage()
        val maxPage=occupiedLast+1
        val next=(pendingDragPage+direction).coerceIn(0,maxPage)
        if(next==pendingDragPage)return
        if(direction>0 && next>occupiedLast) {
            extraHomePages=maxOf(extraHomePages,1)
            db.put("extra_home_pages",extraHomePages.toString())
        }
        pendingDragPage=next
        page=next
        // Require the pointer to leave the edge zone before another page turn.
        edgePagingArmed=false
        dragEdgeDirection=0
        // Keep the last measured target-page geometry during edge paging. Clearing
        // all hit-test maps here makes backward transfers fail before AnimatedContent
        // finishes measuring the incoming page, especially on the first workspace.
        // The bounds are stable screen-space geometry; the page's item model is rebuilt
        // by updateGridDragPreview using pendingDragPage.
        dragPreviewSlots=emptyMap()
    }
    fun clearActiveDragSession() {
        draggingApp=null
        draggingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID
        draggingFromDock=false
        draggingFromFolder=false
        dragSourceFolderId=null
        dragPreviewSlots=emptyMap()
        dragEdgeDirection=0
        edgePagingArmed=true
        pendingDragPage=page
    }
    fun openLauncherSettings() {
        clearActiveDragSession()
        settings=true
    }
    fun updateDragEdge(position:Offset) {
        val width=activity.window.decorView.width.toFloat()
        val edge=width*0.10f
        val direction=when { position.x<=edge -> -1; position.x>=width-edge -> 1; else -> 0 }
        // Re-arm edge paging only after the finger leaves the edge zone.
        // This prevents a stationary pointer from repeatedly switching pages.
        if(direction==0) {
            edgePagingArmed=true
            dragEdgeDirection=0
        } else {
            dragEdgeDirection=if(edgePagingArmed) direction else 0
        }
    }
    // Edge paging is a drag-session loop, not a one-shot timer. The loop is
    // cancelled immediately when the pointer leaves the edge or the drag ends.
    // advanceDragPage bounds the workspace to occupied pages plus one trailing page.
    LaunchedEffect(draggingApp?.packageName, draggingWidgetId, dragEdgeDirection) {
        val direction = dragEdgeDirection
        val dragActive = draggingApp != null || draggingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        if (direction == 0 || !dragActive || !edgePagingArmed) return@LaunchedEffect
        while (dragEdgeDirection == direction &&
            (draggingApp != null || draggingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID)
        ) {
            delay(450L)
            if (dragEdgeDirection != direction ||
                (draggingApp == null && draggingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
            ) break
            val before = pendingDragPage
            advanceDragPage(direction)
            if (pendingDragPage == before) break
            // Require the pointer to leave the edge zone before another page
            // transition can be triggered. This avoids chained page changes while
            // the finger remains pinned against the screen border.
            edgePagingArmed=false
            dragEdgeDirection=0
            delay(320L)
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
            val sourceId=dragSourceFolderId
            val source=folders.firstOrNull{it.id==sourceId}
            if(source!=null){
                val targetPkg=folderMemberBounds.entries.firstOrNull{it.key!=dragged.packageName&&it.value.contains(dragPosition)}?.key
                val ordered=source.packages.filterNot{it==dragged.packageName}.toMutableList()
                val insertAt=targetPkg?.let{ordered.indexOf(it).takeIf{idx->idx>=0}}?:ordered.size
                ordered.add(insertAt.coerceIn(0,ordered.size),dragged.packageName)
                folders=folders.map{if(it.id==source.id)it.copy(packages=ordered)else it};persistFolders()
            }
            draggingApp=null;draggingFromFolder=false;dragSourceFolderId=null;dragEdgeDirection=0;pendingDragPage=page;dragPreviewSlots=emptyMap()
            return
        }
        val targetPage=pendingDragPage.coerceAtLeast(0)
        val targetGrid=gridBoundsFor(targetPage)
        if((targetGrid.width<=0f||targetGrid.height<=0f||!targetGrid.contains(dragPosition))&&!dockBounds.contains(dragPosition)&&dragPosition.y>=uninstallDropZoneHeight){android.widget.Toast.makeText(context,"Drop inside the home screen area",android.widget.Toast.LENGTH_SHORT).show();draggingApp=null;draggingFromDock=false;draggingFromFolder=false;dragSourceFolderId=null;dragPreviewSlots=emptyMap();dragEdgeDirection=0;pendingDragPage=page;return}
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
            val normalizedPage = normalizeHomePages(excludedPackage=dragged.packageName, destinationPage=page)
            page=normalizedPage
            pendingDragPage=normalizedPage
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
            val targetApp=appBounds.entries.firstOrNull {
                it.key!=dragged.packageName && it.key in destinationAppKeys && it.value.contains(dragPosition)
            }?.key
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
        val normalizedPage = normalizeHomePages(
            movingPackage=if (isNowInFolder || dragged.packageName in dockPackages) null else dragged.packageName,
            excludedPackage=if (isNowInFolder || dragged.packageName in dockPackages) dragged.packageName else null,
            destinationPage=targetPage
        )
        page=normalizedPage
        pendingDragPage=normalizedPage
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

    val launcherFont = FontFamily(Font(R.font.san_francisco_font))
    // New home surface: measured grid geometry and a single parent drag owner.
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily=launcherFont)) { Box(Modifier.fillMaxSize().background(Color.Transparent)
        .pointerInput(Unit) {
            var trackedPointer: androidx.compose.ui.input.pointer.PointerId? = null
            var pointerDownPosition = Offset.Zero
            var dragPointerOffset: Offset? = null
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                    if (trackedPointer == null && down != null) { trackedPointer = down.id; pointerDownPosition = down.position; dragPointerOffset = null }
                    val tracked = trackedPointer?.let { id -> event.changes.firstOrNull { it.id == id } }
                    val appDragActive = draggingApp != null
                    val widgetDragActive = draggingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    if ((appDragActive || widgetDragActive) && tracked != null && tracked.pressed) {
                        if (dragPointerOffset == null) dragPointerOffset = if (appDragActive) dragPosition-pointerDownPosition else widgetDragPosition-pointerDownPosition
                        val adjusted = tracked.position + (dragPointerOffset ?: Offset.Zero)
                        if (appDragActive) { dragPosition = adjusted; updateDragEdge(dragPosition) }
                        else widgetDragPosition = adjusted
                    }
                    if (tracked != null && !tracked.pressed && (appDragActive || widgetDragActive)) {
                        if (appDragActive) finishIconDrag() else finishWidgetDrag()
                        trackedPointer = null; dragPointerOffset = null
                    } else if (tracked != null && !tracked.pressed) { trackedPointer = null; dragPointerOffset = null }
                }
            }
        }
        .pointerInput(drawer,controlCenter,settings,openFolderId,page,pageCount,minusScreen,homeStyle,swipeDownAction,draggingApp,draggingWidgetId) {
            if(!drawer&&!controlCenter&&!settings&&openFolderId==null&&draggingApp==null&&draggingWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                    // Horizontal page swipes must work when starting over icons,
                    // folders and widgets. Long-press drag recognizers take ownership
                    // only after touch-slop/long-press; this recognizer handles ordinary
                    // horizontal swipes and deliberately does not consume vertical taps.
                    val edge=with(density){28.dp.toPx()}
                    if(down.position.x<edge||down.position.x>size.width-edge||down.position.y>size.height-edge)return@awaitEachGesture
                    var dx=0f;var dy=0f
                    while(true) {
                        val event=awaitPointerEvent(PointerEventPass.Initial)
                        val c=event.changes.firstOrNull{it.id==down.id}?:break
                        if(!c.pressed)break
                        val d=c.position-c.previousPosition;dx+=d.x;dy+=d.y
                        val slop=viewConfiguration.touchSlop*1.35f
                        if(kotlin.math.abs(dx)>slop&&kotlin.math.abs(dx)>kotlin.math.abs(dy)*1.25f) {
                            page=if(dx>0&&page==0&&minusScreen=="Google Discover")-1 else (page+if(dx<0)1 else -1).coerceIn(0,pageCount-1)
                            c.consume();break
                        }
                        if(kotlin.math.abs(dy)>slop&&kotlin.math.abs(dy)>kotlin.math.abs(dx)*1.25f) {
                            val topRight=down.position.y<size.height*.16f&&down.position.x>size.width*.62f
                            if(dy>0&&topRight&&swipeDownAction=="Control Center")controlCenter=true
                            else if(dy<0&&down.position.y>size.height*.84f&&homeStyle=="With App drawer")drawer=true
                            c.consume();break
                        }
                    }
                }
            }
        }
        ) {
        if(!drawer) Column(Modifier.fillMaxSize().padding(horizontal=18.dp).graphicsLayer {
            renderEffect=if((controlCenter||openFolderId!=null)&&Build.VERSION.SDK_INT>=31)BlurEffect(16f,16f)else null
        }) {
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.combinedClickable(onClick={},onLongClick={if(!lockLayout)homeEditMode=true})) {
                    Text(greeting(),color=fg.copy(alpha=.72f),fontSize=14.sp)
                    Text(SimpleDateFormat("EEEE, MMM d",Locale.getDefault()).format(Date()),color=fg,fontSize=25.sp,fontWeight=FontWeight.SemiBold)
                }
                Surface(shape=CircleShape,color=Color.White.copy(alpha=.52f),modifier=Modifier.size(44.dp).clickable{controlCenter=true}) {                    Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Tune,null,tint=fg)}
                }
            }
            Spacer(Modifier.height(18.dp))
            if(page<0&&minusScreen=="Google Discover") {
                AnimatedVisibility(
                    visible=true,
                    enter=slideInHorizontally(tween(320)){it/3}+fadeIn(tween(220)),
                    exit=slideOutHorizontally(tween(240)){-it/3}+fadeOut(tween(160)),
                    modifier=Modifier.weight(1f).fillMaxWidth()
                ) {
                    AndroidView(
                        factory={ctx->android.webkit.WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.WHITE)
                            val webSettings=this.settings
                            webSettings.setJavaScriptEnabled(true)
                            webSettings.setDomStorageEnabled(true)
                            webSettings.setLoadsImagesAutomatically(true)
                            webSettings.setUseWideViewPort(true)
                            webSettings.setLoadWithOverviewMode(true)
                            webSettings.setUserAgentString(webSettings.getUserAgentString()+" Mobile")
                            this.webViewClient=android.webkit.WebViewClient()
                            this.loadUrl("https://newsblur.com/")
                        }},
                        update={},
                        modifier=Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                    )
                }
            } else AnimatedContent(
                targetState=page,
                transitionSpec={
                    if(targetState>initialState)
                        (slideInHorizontally(tween(260)){it}+fadeIn(tween(170))) togetherWith
                            (slideOutHorizontally(tween(260)){-it}+fadeOut(tween(130)))
                    else
                        (slideInHorizontally(tween(260)){-it}+fadeIn(tween(170))) togetherWith
                            (slideOutHorizontally(tween(260)){it}+fadeOut(tween(130)))
                },
                label="home-screen-page",
                modifier=Modifier.weight(1f).fillMaxWidth()
            ) { renderedPage ->
                BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer{val workspaceScale=if(homeEditMode)0.82f else 1f;scaleX=workspaceScale;scaleY=workspaceScale;transformOrigin=androidx.compose.ui.graphics.TransformOrigin.Center}.pointerInput(lockLayout,homeEditMode){if(!lockLayout){var handled=false;detectTransformGestures{_,_,zoom,_ -> if(!handled&&zoom<0.975f){homeEditMode=true;handled=true}else if(!handled&&zoom>1.025f&&homeEditMode){homeEditMode=false;selectedEditApps=setOf();gridDialog=false;batchMoveDialog=false;batchFolderDialog=false;handled=true}}}}.onGloballyPositioned{
                    val bounds=it.boundsInRoot();homeGridBounds=bounds;homeGridBoundsByPage[renderedPage]=bounds
                    draggingApp?.let{app->if(renderedPage==pendingDragPage)updateGridDragPreview(dragPosition,"app:"+app.packageName)}
                }) {
                val systemHeaderRows=if(renderedPage==0)2 else 0
                val gridRows=effectiveRows+systemHeaderRows
                val cw=maxWidth/columns.coerceAtLeast(1);val ch=maxHeight/gridRows.coerceAtLeast(1)
                val pageApps=pageAppSlices.getOrNull(renderedPage.coerceAtLeast(0)).orEmpty()
                val pageFolders=folders.filter{it.page==renderedPage}
                val pageWidgets=homeWidgets.filter{it.second==renderedPage}.mapNotNull{(id,_)->AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.let{id to it}}
                val tiles=buildList {
                    pageApps.forEachIndexed { i, app ->
                        val appSlot=dragPreviewSlots["app:"+app.packageName] ?: (appSlotAssignments[app.packageName] ?: i)
                        add(HomeGridTile("app:"+app.packageName,appSlot))
                    }
                    pageFolders.forEach{add(HomeGridTile("folder:"+it.id,dragPreviewSlots["folder:"+it.id]?:it.slot))}
                    pageWidgets.forEach{(id,info)->
                        val size=widgetSizes[id]?: (info.minWidth.coerceAtLeast(110).dp to info.minHeight.coerceAtLeast(90).dp)
                        add(HomeGridTile("widget:"+id,dragPreviewSlots["widget:"+id]?:widgetSlotAssignments[id]?:pageApps.size+pageFolders.size,kotlin.math.ceil(size.first.value/cw.value.coerceAtLeast(1f)).toInt().coerceIn(1,columns),kotlin.math.ceil(size.second.value/ch.value.coerceAtLeast(1f)).toInt().coerceIn(1,gridRows)))
                    }
                    draggingApp?.takeIf{moving->renderedPage==pendingDragPage&&pageApps.none{it.packageName==moving.packageName}}?.let{add(HomeGridTile("app:"+it.packageName,dragPreviewSlots["app:"+it.packageName]?:size))}
                }
                val cells=remember(tiles,columns,gridRows,fillCells){calculateHomeGridCells(tiles,columns,gridRows,fillCells)}
                pageApps.forEachIndexed{index,app->
                    val cell=cells["app:"+app.packageName]?:return@forEachIndexed
                    val x by animateDpAsState(cw*cell.column,spring(dampingRatio=.82f,stiffness=460f),label="new-home-app-x")
                    val y by animateDpAsState(ch*cell.row,spring(dampingRatio=.82f,stiffness=460f),label="new-home-app-y")
                    Box(Modifier.offset(x,y).size(cw,ch).graphicsLayer{alpha=if(draggingApp?.packageName==app.packageName) .12f else 1f},contentAlignment=Alignment.Center) {
                        AppIcon(app=app,labels=showLabels,fg=fg,index=index,animationSpeed=animationSpeed,size=62.dp,draggableEnabled=true,animateOnEntry=false,
                            onBounds={key,rect->appBounds[key]=rect},
                            onDragStart={ if(!lockLayout){ val origin=appBounds[app.packageName]?:Rect.Zero; draggingApp=app;dragOriginBounds=origin;dragOriginPage=page;pendingDragPage=page;dragPosition=origin.center;draggingFromDock=false;draggingFromFolder=false;dragSourceFolderId=null;edgePagingArmed=true;homeEditMode=true } },
                            onDrag={delta-> if(draggingApp?.packageName==app.packageName){dragPosition+=delta;updateDragEdge(dragPosition)} },
                            onDragEnd={finishIconDrag()},onDragCancel={ /* Parent drag layer retains pointer ownership across page disposal. */ },onLongClick={if(!lockLayout) homeEditMode=true },onClick={if(homeEditMode){selectedEditApps=if(app.packageName in selectedEditApps)selectedEditApps-app.packageName else selectedEditApps+app.packageName}else if(app.packageName=="internal:settings")openLauncherSettings() else activity.launch(app)})
                        if(homeEditMode){Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(if(app.packageName in selectedEditApps)Color(0xFF1685F8)else Color.White.copy(alpha=.7f)),contentAlignment=Alignment.Center){Icon(if(app.packageName in selectedEditApps)Icons.Default.Check else Icons.Default.Add,null,tint=if(app.packageName in selectedEditApps)Color.White else Color.Gray,modifier=Modifier.size(15.dp))}}
                    }
                }
                pageFolders.forEach{folder->
                    val cell=cells["folder:"+folder.id]?:return@forEach
                    val x by animateDpAsState(cw*cell.column,spring(dampingRatio=.82f,stiffness=460f),label="new-home-folder-x")
                    val y by animateDpAsState(ch*cell.row,spring(dampingRatio=.82f,stiffness=460f),label="new-home-folder-y")
                    Box(Modifier.offset(x,y).size(cw,ch).onGloballyPositioned{folderBounds[folder.id]=it.boundsInRoot()},contentAlignment=Alignment.Center){FolderTile(folder,apps,fg){openFolderId=folder.id}}
                }
                pageWidgets.forEach{(id,info)->
                    val cell=cells["widget:"+id]?:return@forEach
                    val size=widgetSizes[id]?:((cw*minOf(2,columns)).coerceAtLeast(110.dp) to info.minHeight.coerceAtLeast(90).dp)
                    val sx=kotlin.math.ceil(size.first.value/cw.value.coerceAtLeast(1f)).toInt().coerceIn(1,columns)
                    val sy=kotlin.math.ceil(size.second.value/ch.value.coerceAtLeast(1f)).toInt().coerceIn(1,effectiveRows)
                    val x by animateDpAsState(cw*cell.column,spring(dampingRatio=.82f,stiffness=420f),label="new-home-widget-x")
                    val y by animateDpAsState(ch*cell.row,spring(dampingRatio=.82f,stiffness=420f),label="new-home-widget-y")
                    Box(Modifier.offset(x,y).size(cw*sx,ch*sy).graphicsLayer{alpha=if(draggingWidgetId==id) .12f else 1f}.pointerInput(id,lockLayout){if(!lockLayout) detectDragGesturesAfterLongPress(onDragStart={val origin=widgetBounds[id]?:Rect.Zero;draggingWidgetId=id;pendingDragPage=renderedPage;page=renderedPage;widgetDragPosition=origin.center;widgetCanvasBounds=origin;draggingApp=null;dragEdgeDirection=0;edgePagingArmed=true;homeEditMode=true},onDragEnd={finishWidgetDrag()},onDragCancel={ /* Parent pointer layer retains widget drag through page transitions. */ },onDrag={change,delta->change.consume();widgetDragPosition+=delta;updateDragEdge(widgetDragPosition)})}.onGloballyPositioned{widgetBounds[id]=it.boundsInRoot();if(draggingWidgetId==id)widgetCanvasBounds=it.boundsInRoot()}){
                        AndroidView(factory={activity.widgetHost.createView(it,id,info)},modifier=Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(28.dp).clip(CircleShape).background(Color(0x88333333)).onGloballyPositioned{widgetHandleBounds[id]=it.boundsInRoot()}.pointerInput(id,lockLayout){if(!lockLayout) detectDragGestures(onDragStart={resizingWidgetId=id},onDragEnd={resizingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID;db.put("home_widget_sizes",widgetSizes.entries.joinToString(";"){it.key.toString()+":"+it.value.first.value+":"+it.value.second.value})},onDragCancel={resizingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID},onDrag={change,delta->change.consume();val oldSize=widgetSizes[id]?:((cw*minOf(2,columns)).coerceAtLeast(110.dp) to info.minHeight.coerceAtLeast(90).dp);val next=(oldSize.first+with(density){delta.x.toDp()}).coerceIn(cw,(cw*columns).coerceAtLeast(cw));val nextH=(oldSize.second+with(density){delta.y.toDp()}).coerceIn(ch,(ch*effectiveRows).coerceAtLeast(ch));widgetSizes=widgetSizes+(id to (next to nextH))})},contentAlignment=Alignment.Center){Text("⠿",color=Color.White,fontSize=18.sp)}
                    }
                }
            }
            }
            Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                repeat(pageCount){i->Box(Modifier.padding(horizontal=4.dp).size(if(page==i)8.dp else 6.dp).clip(CircleShape).background(if(page==i)Color(0xFF1685F8)else fg.copy(alpha=.28f)).clickable{page=i})}
            }
            AnimatedVisibility(homeEditMode,enter=fadeIn(tween(160))+scaleIn(spring(dampingRatio=.82f)),exit=fadeOut(tween(120))){
                Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                        Button(onClick={homeEditMode=false;selectedEditApps=setOf();gridDialog=false;batchMoveDialog=false;batchFolderDialog=false},shape=RoundedCornerShape(28.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF1685F8))){Text("Done",fontWeight=FontWeight.SemiBold)}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){
                        pageAppSlices.indices.forEach { i -> TextButton(onClick={page=i;pendingDragPage=i}){Text((i+1).toString(),color=if(page==i)Color(0xFF1685F8)else fg)} }
                        TextButton(onClick={page=pageCount-1;pendingDragPage=pageCount-1;extraHomePages=maxOf(extraHomePages,1);db.put("extra_home_pages",extraHomePages.toString())}){Text("+")}
                    }
                    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(30.dp),color=Color.Black.copy(alpha=.28f),border=BorderStroke(1.dp,Color.White.copy(alpha=.16f))){
                        Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                            @Composable fun EditAction(icon:ImageVector,label:String,onClick:()->Unit){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable(onClick=onClick)){Surface(shape=CircleShape,color=Color.Black.copy(alpha=.28f),modifier=Modifier.size(58.dp)){Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=Color.White,modifier=Modifier.size(25.dp))}};Spacer(Modifier.height(5.dp));Text(label,color=Color.White,fontSize=12.sp)}}
                            EditAction(Icons.Default.Wallpaper,"Wallpaper"){try{activity.startActivity(Intent(Intent.ACTION_SET_WALLPAPER))}catch(_:Exception){}}
                            EditAction(Icons.Default.AutoAwesome,"Effects"){gridDialog=true}
                            EditAction(Icons.Default.Widgets,"Widgets"){widgetProviders=activity.availableHomeWidgets();widgetPickerOpen=true}
                            EditAction(Icons.Default.Settings,"Settings"){openLauncherSettings()}
                        }
                    }
                    if(selectedEditApps.isNotEmpty())Surface(shape=RoundedCornerShape(20.dp),color=if(dark)Color(0xDD252936)else Color(0xEFFFFFFF),shadowElevation=6.dp){
                        Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                            Text("${selectedEditApps.size} selected",modifier=Modifier.align(Alignment.CenterVertically))
                            TextButton(onClick={batchMoveDialog=true}){Text("Move")}
                            TextButton(onClick={batchFolderDialog=true}){Text("Folder")}
                            TextButton(onClick={selectedEditApps=setOf()}){Text("Clear")}
                        }
                    }
                }
            }
            Surface(Modifier.fillMaxWidth().padding(bottom=8.dp).onGloballyPositioned{dockBounds=it.boundsInRoot()},shape=RoundedCornerShape(26.dp),color=Color.White.copy(alpha=.20f),border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=if(dark).22f else .42f))){
                Row(Modifier.fillMaxWidth().height(78.dp).padding(horizontal=6.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                    dockPackages.mapNotNull{pkg->apps.firstOrNull{it.packageName==pkg}}.forEach{app->
                        AppIcon(app=app,labels=false,fg=fg,size=49.dp,draggableEnabled=true,animateOnEntry=false,
                            onDragStart={if(!lockLayout){val origin=dockAppBounds[app.packageName]?:Rect.Zero;draggingApp=app;dragOriginBounds=origin;dragOriginPage=page;pendingDragPage=page;dragPosition=origin.center;draggingFromDock=true;draggingFromFolder=false;dragSourceFolderId=null;edgePagingArmed=true;homeEditMode=true}},
                            onDrag={delta->if(draggingApp?.packageName==app.packageName){dragPosition+=delta;updateDragEdge(dragPosition)}},onDragEnd={finishIconDrag()},onDragCancel={clearActiveDragSession()},
                            onLongClick={if(!lockLayout)homeEditMode=true}, onBounds={key,rect->dockAppBounds[key]=rect},
                            onClick={if(app.packageName=="internal:settings")settings=true else activity.launch(app)})
                    }
                    if(dockPackages.isEmpty())Text("Hold an app to edit your Home Screen",color=fg.copy(alpha=.6f),fontSize=11.sp)
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom=4.dp),contentAlignment=Alignment.Center){Box(Modifier.width(108.dp).height(5.dp).clip(CircleShape).background(fg.copy(alpha=.48f)).clickable{if(homeStyle=="With App drawer")drawer=true})}
        }
        if(draggingApp!=null){
            val app=draggingApp!!;val bitmap=remember(app.packageName){app.icon.toBitmap(144,144).asImageBitmap()};val half=with(density){34.dp.toPx()}
            Image(bitmap,contentDescription="Moving app",modifier=Modifier.offset{IntOffset((dragPosition.x-half).toInt(),(dragPosition.y-half).toInt())}.size(68.dp).clip(RoundedCornerShape(18.dp)).shadow(12.dp,RoundedCornerShape(18.dp)))
        }
        AnimatedVisibility(visible=draggingApp!=null||draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID,enter=fadeIn()+scaleIn(initialScale=.82f),exit=fadeOut()+scaleOut(targetScale=.86f),modifier=Modifier.align(Alignment.TopCenter)){
            Surface(shape=RoundedCornerShape(28.dp),color=Color(0xFF29242E).copy(alpha=.92f),shadowElevation=10.dp,modifier=Modifier.padding(top=18.dp).width(112.dp).height(58.dp)){
                Row(Modifier.fillMaxSize().padding(horizontal=12.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=Color(0xFFE65C68),modifier=Modifier.size(34.dp)){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Delete,null,tint=Color.White,modifier=Modifier.size(19.dp))}};Text(if(draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID)"Remove" else "Uninstall",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=8.dp))}
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
                onClose={settings=false;clearActiveDragSession()},
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
                                        draggableEnabled=true,
                                        onDragStart={if(!lockLayout){val origin=folderMemberBounds[app.packageName]?:Rect.Zero;draggingApp=app;dragOriginBounds=origin;dragOriginPage=folder.page;pendingDragPage=folder.page;dragPosition=origin.center;draggingFromDock=false;draggingFromFolder=true;dragSourceFolderId=folder.id;edgePagingArmed=true;homeEditMode=true}},
                                        onDrag={delta->if(draggingApp?.packageName==app.packageName){dragPosition+=delta;updateDragEdge(dragPosition)}},onDragEnd={finishIconDrag()},onDragCancel={clearActiveDragSession()},
                                        onClick={
                                            openFolderId=null
                                            if(app.packageName=="internal:settings") openLauncherSettings() else activity.launch(app)
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Use the organize menu to move apps",color=Color(0xFF66707C),fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
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
                            Button(onClick={pendingUninstallApp=null},modifier=Modifier.weight(1f)) { Text("No",color=Color.White.copy(alpha=.82f)) }
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
        if(gridDialog){
            AlertDialog(onDismissRequest={gridDialog=false},containerColor=(if(isSystemInDarkTheme())Color(0xFF29292E) else Color(0xFFF8F8FC)),shape=RoundedCornerShape(28.dp),title={Text("Home screen grid",fontSize=23.sp,fontWeight=FontWeight.SemiBold,color=if(isSystemInDarkTheme())Color.White else Color(0xFF19191D))},
                text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("Columns: $columns")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={columns=(columns-1).coerceAtLeast(2);db.put("grid_columns",columns.toString())}){Text("−")};Text("$columns",modifier=Modifier.align(Alignment.CenterVertically));Button(onClick={columns=(columns+1).coerceAtMost(8);db.put("grid_columns",columns.toString())}){Text("+")}}
                    Text("Rows: $rows")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={rows=(rows-1).coerceAtLeast(3);db.put("grid_rows",rows.toString())}){Text("−")};Text("$rows",modifier=Modifier.align(Alignment.CenterVertically));Button(onClick={rows=(rows+1).coerceAtMost(12);db.put("grid_rows",rows.toString())}){Text("+")}}
                }},confirmButton={Button(onClick={gridDialog=false}){Text("Done")}})
        }
        if(batchMoveDialog){
            AlertDialog(onDismissRequest={batchMoveDialog=false},containerColor=(if(isSystemInDarkTheme())Color(0xFF29292E) else Color(0xFFF8F8FC)).copy(alpha=0.90f),shape=RoundedCornerShape(30.dp),
                title={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Move selected apps",fontSize=24.sp,fontWeight=FontWeight.Bold,color=if(dark)Color.White else Color(0xFF19151F));Text("Choose a home screen",fontSize=14.sp,color=if(dark)Color(0xFFB0B0B8) else Color(0xFF77717D))}},
                text={Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){(0 until pageCount).forEach{dest->Surface(onClick={val selected=selectedEditApps.toList();val destinationApps=pageAppSlices.getOrNull(dest).orEmpty().filterNot{it.packageName in selected};if(selected.size+destinationApps.size>columns*effectiveRows){android.widget.Toast.makeText(context,"Not enough space on this home screen",android.widget.Toast.LENGTH_SHORT).show()}else{selected.forEach{pkg->appPageAssignments=appPageAssignments+(pkg to dest)};val tiles=destinationApps.mapIndexed{index,app->HomeGridTile("app:"+app.packageName,appSlotAssignments[app.packageName]?:index)}+selected.mapIndexed{index,pkg->HomeGridTile("app:"+pkg,columns*effectiveRows-selected.size+index)};val placed=HomeWorkspaceEngine.layout(tiles,columns,effectiveRows,fillCells);placed.forEach{(key,cell)->if(key.startsWith("app:"))appSlotAssignments=appSlotAssignments+(key.removePrefix("app:") to cell.row*columns+cell.column)};db.put("app_pages",appPageAssignments.entries.joinToString(";"){it.key+":"+it.value});db.put("app_slots",appSlotAssignments.entries.joinToString(";"){it.key+":"+it.value});page=dest;pendingDragPage=dest;batchMoveDialog=false;selectedEditApps=setOf()}},color=if(dark)Color(0xFF3A3544) else Color(0xFFE6DFF2),shape=RoundedCornerShape(18.dp)){Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(12.dp),color=Color(0xFF6750A4),modifier=Modifier.size(42.dp)){Box(contentAlignment=Alignment.Center){Text((dest+1).toString(),color=Color.White,fontWeight=FontWeight.Bold)}};Column(Modifier.weight(1f).padding(start=12.dp)){Text("Home Screen ${dest+1}",fontWeight=FontWeight.SemiBold,color=if(dark)Color.White else Color(0xFF292331));Text(if(dest==page)"Current screen" else "Tap to move",fontSize=12.sp,color=if(dark)Color(0xFFB0B0B8) else Color(0xFF77717D))};Icon(Icons.Default.ChevronRight,null,tint=Color(0xFF6750A4))}}}}},
                confirmButton={TextButton(onClick={batchMoveDialog=false}){Text("Cancel",color=Color(0xFF0A84FF),fontWeight=FontWeight.SemiBold)}})
        }
        if(batchFolderDialog){
            AlertDialog(
                onDismissRequest={batchFolderDialog=false},
                containerColor=(if(isSystemInDarkTheme())Color(0xFF29292E) else Color(0xFFF8F8FC)).copy(alpha=0.90f),
                shape=RoundedCornerShape(30.dp),
                title={ Text("Create folder",fontSize=24.sp,fontWeight=FontWeight.Bold,color=if(dark)Color.White else Color(0xFF19151F)) },
                text={
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(color=if(dark)Color(0xFF3A3544) else Color(0xFFE6DFF2),shape=RoundedCornerShape(18.dp)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder,null,tint=Color(0xFF6750A4),modifier=Modifier.size(32.dp))
                                Column(Modifier.padding(start=12.dp)) {
                                    Text("${selectedEditApps.size} apps selected",fontWeight=FontWeight.SemiBold,color=if(dark)Color.White else Color(0xFF292331))
                                    Text("Home Screen ${page+1}",fontSize=13.sp,color=if(dark)Color(0xFFB0B0B8) else Color(0xFF77717D))
                                }
                            }
                        }
                        Text("Combine these apps into a folder on this screen.",color=if(dark)Color(0xFFB0B0B8) else Color(0xFF625B68),fontSize=14.sp)
                    }
                },
                confirmButton={
                    Button(
                        onClick={
                            if(selectedEditApps.size>=2){
                                val chosen=selectedEditApps.toList()
                                val slot=chosen.firstOrNull()?.let{appSlotAssignments[it]}?:0
                                folders=folders+HomeFolder("folder:"+System.currentTimeMillis(),"Folder",chosen,page,slot)
                                persistFolders()
                                appPageAssignments=appPageAssignments-chosen
                                appSlotAssignments=appSlotAssignments-chosen
                                db.put("app_pages",appPageAssignments.entries.joinToString(";"){it.key+":"+it.value})
                                db.put("app_slots",appSlotAssignments.entries.joinToString(";"){it.key+":"+it.value})
                                selectedEditApps=setOf()
                            }
                            batchFolderDialog=false
                        },
                        shape=RoundedCornerShape(16.dp)
                    ){ Text("Create folder") }
                },
                dismissButton={TextButton(onClick={batchFolderDialog=false}){Text("Cancel",color=Color(0xFF0A84FF))}}
            )
        }
        if(widgetPickerOpen){
            ModalBottomSheet(onDismissRequest={widgetPickerOpen=false;widgetSearch="";widgetPickerDrag=null},containerColor=if(dark) Color(0xFF202126) else Color(0xFFF5F5FA),contentColor=if(dark) Color.White else Color(0xFF1C1C1E),modifier=Modifier.shadow(28.dp,RoundedCornerShape(topStart=32.dp,topEnd=32.dp)),shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp),dragHandle={Box(Modifier.padding(vertical=10.dp).width(42.dp).height(5.dp).clip(CircleShape).background(Color.Gray))}){
                Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=28.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Add widgets",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),color=if(dark) Color.White else Color(0xFF1C1C1E));Icon(Icons.Default.Search,null,tint=if(dark) Color.White else Color(0xFF1C1C1E))}
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(value=widgetSearch,onValueChange={widgetSearch=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Search widgets",color=if(dark) Color.Gray else Color(0xFF77777D))},singleLine=true,leadingIcon={Icon(Icons.Default.Search,null,tint=Color.Gray)},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=if(dark) Color.White else Color(0xFF1C1C1E),unfocusedTextColor=if(dark) Color.White else Color(0xFF1C1C1E),focusedBorderColor=if(dark) Color.White else Color(0xFF8E8E93),unfocusedBorderColor=if(dark) Color.DarkGray else Color(0xFFB8B8BD)))
                    Spacer(Modifier.height(16.dp))
                    if(widgetProviders.isEmpty()) Text("No compatible widgets are currently installed.",modifier=Modifier.padding(16.dp),color=if(dark) Color.White else Color(0xFF1C1C1E))
                    else LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.heightIn(max=620.dp),contentPadding=PaddingValues(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
                        items(widgetProviders.filter{provider->provider.loadLabel(context.packageManager).toString().contains(widgetSearch,true)||provider.provider.packageName.contains(widgetSearch,true)},key={it.provider.flattenToString()}){
                            provider->
                            val label=provider.loadLabel(context.packageManager).toString()
                            val preview=remember(provider){try{provider.loadPreviewImage(context,context.resources.displayMetrics.densityDpi)}catch(_:Exception){null}}
                            val appIcon=remember(provider){try{context.packageManager.getApplicationIcon(provider.provider.packageName)}catch(_:Exception){null}}
                            Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth().pointerInput(provider){detectDragGesturesAfterLongPress(onDragStart={_->widgetPickerDrag=provider},onDragEnd={val picked=widgetPickerDrag;widgetPickerDrag=null;if(picked!=null){widgetPickerOpen=false;widgetSearch="";activity.addHomeWidget(page,picked)}},onDragCancel={widgetPickerDrag=null},onDrag={change,_->change.consume()})}){
                                Surface(Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(24.dp)),shape=RoundedCornerShape(24.dp),color=if(dark) Color(0xFF24242A) else Color(0xFFE8E8EE)){
                                    Box(contentAlignment=Alignment.Center){
                                        if(preview!=null)Image(preview.toBitmap().asImageBitmap(),null,Modifier.fillMaxSize().padding(6.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                                        else if(appIcon!=null)Image(appIcon.toBitmap().asImageBitmap(),null,Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)))
                                        else Icon(Icons.Default.Widgets,null,tint=Color.White,modifier=Modifier.size(64.dp))
                                    }
                                }
                                Spacer(Modifier.height(8.dp));Text(label,fontWeight=FontWeight.SemiBold,maxLines=1,color=if(dark) Color.White else Color(0xFF1C1C1E),textAlign=TextAlign.Center);Text("Widget · ${provider.provider.packageName}",fontSize=11.sp,color=if(dark) Color.Gray else Color(0xFF77777D),maxLines=1,textAlign=TextAlign.Center)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick={widgetPickerOpen=false;widgetSearch="";activity.addHomeWidget(page,provider)},shape=RoundedCornerShape(24.dp),colors=ButtonDefaults.buttonColors(containerColor=if(dark) Color(0xFF173B59) else Color(0xFFE1ECFF),contentColor=Color(0xFF1683F5)),modifier=Modifier.widthIn(min=90.dp)){Text("Add")}
                            }
                        }
                    }
                    Text("Tip: long-press a widget card and release to add it to the current home screen.",fontSize=12.sp,color=if(dark) Color.Gray else Color(0xFF77777D),modifier=Modifier.padding(top=8.dp))
                }
            }
        }
        if(draggingApp!=null || draggingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID){
            Box(Modifier.fillMaxSize().zIndex(20f)){
                val dragged=draggingApp
                if(dragged!=null){
                    Image(dragged.icon.toBitmap(144,144,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap(),contentDescription=dragged.label,
                        modifier=Modifier.offset{IntOffset((dragPosition.x-36.dp.toPx()).toInt(),(dragPosition.y-36.dp.toPx()).toInt())}.size(72.dp).shadow(18.dp,CircleShape).clip(CircleShape).alpha(.94f))
                }else{
                    Surface(modifier=Modifier.offset{IntOffset((widgetDragPosition.x-60.dp.toPx()).toInt(),(widgetDragPosition.y-60.dp.toPx()).toInt())}.size(120.dp,96.dp),shape=RoundedCornerShape(22.dp),color=Color.White.copy(alpha=.28f),border=BorderStroke(2.dp,Color.White.copy(alpha=.85f)),shadowElevation=18.dp){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Widgets,null,tint=fg,modifier=Modifier.size(42.dp))}}
                }
            }
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
}
