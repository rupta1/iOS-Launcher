package com.example.ioslauncher

import android.graphics.drawable.Drawable
import android.appwidget.AppWidgetManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import java.util.Locale

data class InstalledApp(val label:String,val packageName:String,val icon:Drawable)
data class HomeFolder(val id:String,val title:String,val packages:List<String>)
data class HomeGridTile(val key:String,val slot:Int,val spanX:Int=1,val spanY:Int=1)
data class HomeGridCell(val column:Int,val row:Int)

@Composable
fun LauncherRoot(db:LauncherDb) {
    val activity=LocalContext.current as? MainActivity
    val context=LocalContext.current
    var apps by remember { mutableStateOf(activity?.loadApps().orEmpty()) }
    var edit by remember { mutableStateOf(false) }
    var drawer by remember { mutableStateOf(false) }
    var control by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(db.get("home_page","0").toIntOrNull()?:0) }
    var pages by remember { mutableStateOf((db.get("home_pages","3").toIntOrNull()?:3).coerceAtLeast(1)) }
    var cols by remember { mutableIntStateOf(db.get("home_columns","4").toIntOrNull()?:4) }
    var rows by remember { mutableIntStateOf(db.get("home_rows","5").toIntOrNull()?:5) }
    var labels by remember { mutableStateOf(db.get("home_labels","true")!="false") }
    var dark by remember { mutableStateOf(db.get("dark","false")=="true") }
    var order by remember { mutableStateOf(db.get("home_order","")) }
    var dragApp by remember { mutableStateOf<InstalledApp?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val scope=rememberCoroutineScope()
    val wallpaper=remember(activity?.wallpaperRevision) { runCatching { android.app.WallpaperManager.getInstance(context).drawable }.getOrNull() }
    val bg=if(dark) Color(0xFF12131A) else Color(0xFFF3F4F7)
    val fg=if(dark) Color.White else Color(0xFF17171D)
    val sorted=remember(apps,order) {
        val ids=order.split("|").filter{it.isNotBlank()}
        if(ids.isEmpty()) apps else apps.sortedBy { ids.indexOf(it.packageName).let{idx->if(idx<0)99999 else idx} }
    }
    val pageItems=remember(sorted,page,cols,rows) {
        sorted.filter { app -> (order.contains(app.packageName) || page==0) }.take(cols*rows)
    }
    fun persistOrder(list:List<InstalledApp>) { order=list.joinToString("|"){it.packageName};db.put("home_order",order) }
    fun openApp(app:InstalledApp) { activity?.launch(app) }
    BackHandler(drawer||control||settings||edit) {
        when { settings -> settings=false; control -> control=false; drawer -> drawer=false; edit -> edit=false }
    }
    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues()).padding(bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(),fontSize=16.sp,color=fg.copy(.65f))
                    Text(android.text.format.DateFormat.format("EEEE, MMM d",java.util.Date()).toString(),fontSize=22.sp,color=fg,fontWeight=FontWeight.SemiBold)
                }
                IconButton(onClick={settings=true}) { Icon(Icons.Default.Tune,null,tint=fg) }
            }
            AnimatedContent(targetState=page,transitionSpec={
                (slideInHorizontally { if(it>0) it else -it }+fadeIn()) togetherWith
                (slideOutHorizontally { if(it>0) -it else it }+fadeOut())
            },label="page") { p ->
                val items=if(p==0) sorted.take(cols*rows) else sorted.drop(p*cols*rows).take(cols*rows)
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp).pointerInput(page,pages) {
                    var total=0f
                    detectHorizontalDragGestures(onDragStart={total=0f},onDragEnd={ if(total < -70f) { if(page<pages-1) { page++;db.put("home_page",page.toString()) } } else if(total>70f) { if(page>0) { page--;db.put("home_page",page.toString()) } } },onHorizontalDrag={ change,amount -> total+=amount })
                }) {
                    if(p==0) {
                        Row(Modifier.fillMaxWidth().padding(5.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            HomeWidget("TODAY",android.text.format.DateFormat.format("EEE, MMM d",java.util.Date()).toString(),"Your day at a glance",Icons.Default.CalendarMonth,Modifier.weight(1f),Color(0xFF5178D8))
                            HomeWidget("WEATHER","--°","Weather widget requires provider",Icons.Default.Cloud,Modifier.weight(1f),Color(0xFF40A0D8))
                        }
                    }
                    LazyVerticalGrid(columns=GridCells.Fixed(cols),modifier=Modifier.weight(1f).fillMaxWidth(),state=rememberLazyGridState(),
                        verticalArrangement=Arrangement.spacedBy(20.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(vertical=18.dp)) {
                        items(items,key={it.packageName}) { app ->
                            AppIcon(app,labels,fg,index=0,animateOnEntry=false,draggableEnabled=false,onLongClick={edit=true;dragApp=app}) {
                                if(edit) { edit=false } else openApp(app)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                repeat(pages) { index ->
                    Box(Modifier.padding(horizontal=4.dp).size(if(index==page)8.dp else 6.dp).clip(RoundedCornerShape(50)).background(if(index==page)fg else fg.copy(.3f)).clickable { page=index;db.put("home_page",index.toString()) })
                }
                if(edit) IconButton(onClick={pages++;page=pages-1;db.put("home_pages",pages.toString())}) { Icon(Icons.Default.Add,null,tint=fg) }
            }
            Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp),shape=RoundedCornerShape(30.dp),color=if(dark) Color.White.copy(.13f) else Color.White.copy(.75f)) {
                Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    val dock=listOf("Phone","Messages","Safari","Photos")
                    dock.forEach { label ->
                        val app=sorted.firstOrNull{it.label.contains(label,true)}
                        if(app!=null) AppIcon(app,false,fg,size=48.dp,animateOnEntry=false,draggableEnabled=false,onClick={openApp(app)})
                        else IconButton(onClick={drawer=true}) { Icon(Icons.Default.Apps,null,tint=fg) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(34.dp).pointerInput(Unit) {
            detectVerticalDragGestures(onDragEnd={ drawer=true }) { change, amount -> if(amount< -18) drawer=true }
        })
        if(edit) Surface(Modifier.align(Alignment.TopCenter).padding(top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+8.dp),shape=RoundedCornerShape(22.dp),color=Color(0xFFEE4545)) {
            Row(Modifier.clickable{edit=false}.padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { Text("Done",color=Color.White,fontWeight=FontWeight.SemiBold) }
        }
        if(edit) Surface(Modifier.align(Alignment.TopCenter).padding(top=100.dp),shape=RoundedCornerShape(24.dp),color=Color(0xFF25262D)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(12.dp)) {
                TextButton(onClick={page=(page-1).coerceAtLeast(0)}){Text("‹ Page")}
                Text("Edit Home",color=Color.White,modifier=Modifier.padding(8.dp))
                TextButton(onClick={pages++;page=pages-1;db.put("home_pages",pages.toString())}){Text("+ Page")}
            }
        }
        if(drawer) AnimatedVisibility(visible=true,enter=fadeIn()+slideInVertically{it},exit=fadeOut()+slideOutVertically{it},modifier=Modifier.fillMaxSize()) {
            AppDrawer(sorted,search,{search=it},Color.White,Color(0xFF111827),true,0.88f,16f,{openApp(it)},{drawer=false})
        }
        if(control) AnimatedVisibility(visible=true,enter=fadeIn()+scaleIn(initialScale=.96f),exit=fadeOut()+scaleOut(targetScale=.96f),modifier=Modifier.fillMaxSize()) {
            val ds=DeviceStateReader.read(context)
            ControlCenter(ds,dark,{ dark=it;db.put("dark",it.toString())},{control=false})
        }
        if(settings) LauncherQuickSettings(cols,rows,labels,dark,{cols=it;db.put("home_columns",it.toString())},{rows=it;db.put("home_rows",it.toString())},{labels=it;db.put("home_labels",it.toString())},{dark=it;db.put("dark",it.toString())},{settings=false})
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress={edit=true},onDoubleTap={edit=true}) })
    }
}

@Composable
private fun LauncherQuickSettings(cols:Int,rows:Int,labels:Boolean,dark:Boolean,onCols:(Int)->Unit,onRows:(Int)->Unit,onLabels:(Boolean)->Unit,onDark:(Boolean)->Unit,onClose:()->Unit) {
    val fg=if(dark)Color.White else Color(0xFF202126)
    Column(Modifier.fillMaxSize().background(if(dark)Color(0xFF15151A) else Color(0xFFF4F4F8)).padding(WindowInsets.statusBars.asPaddingValues()).padding(22.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=fg)};Text("Home Settings",fontSize=25.sp,color=fg,fontWeight=FontWeight.SemiBold)}
        Spacer(Modifier.height(20.dp))
        Text("Grid",color=fg,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(3,4,5,6).forEach { TextButton(onClick={onCols(it)}) { Text("$it columns") } } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(4,5,6,7).forEach { TextButton(onClick={onRows(it)}) { Text("$it rows") } } }
        Row(Modifier.fillMaxWidth().clickable{onLabels(!labels)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("Show icon labels",Modifier.weight(1f),color=fg);Switch(labels,onLabels)}
        Row(Modifier.fillMaxWidth().clickable{onDark(!dark)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("Dark appearance",Modifier.weight(1f),color=fg);Switch(dark,onDark)}
        Spacer(Modifier.weight(1f));Button(onClick=onClose,modifier=Modifier.fillMaxWidth()){Text("Done")}
    }
}
