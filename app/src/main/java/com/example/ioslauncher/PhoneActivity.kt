package com.example.ioslauncher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.graphics.drawable.ColorDrawable
import android.graphics.BitmapFactory
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DialContact(val id:Long,val name:String,val number:String,val starred:Boolean,val photoUri:String?=null)
private data class DialCall(val id:Long,val name:String,val number:String,val type:Int,val date:Long,val duration:Long,val account:String)
private data class DialSim(val id:Int,val name:String,val accountId:String?)
private data class DialContactDetail(val id:Long,val name:String,val phones:List<Pair<String,String>>,val emails:List<Pair<String,String>>,val addresses:List<Pair<String,String>>,val birthday:String?,val photoUri:String?=null,val starred:Boolean=false)

class PhoneActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark=isSystemInDarkTheme()
            MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) { IosDialer() }
        }
    }
}

@Composable
private fun IosDialer() {
    val ctx=LocalContext.current
    val dark=isSystemInDarkTheme()
    val bg=if(dark) Color.Black else Color(0xFFF5F5F8)
    val card=if(dark) Color(0xFF242426) else Color.White
    val field=if(dark) Color(0xFF303034) else Color(0xFFE9E9EE)
    val fg=if(dark) Color(0xFFF5F5F7) else Color(0xFF17171B)
    val muted=if(dark) Color(0xFF99999F) else Color(0xFF85858D)
    val line=if(dark) Color(0xFF363639) else Color(0xFFE5E5EA)
    val green=Color(0xFF30C65B); val blue=Color(0xFF3987FF); val red=Color(0xFFFF453A)
    val prefs=remember { ctx.getSharedPreferences("phone_preferences",Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf("Recents") }
    var page by remember { mutableStateOf("main") }
    var query by remember { mutableStateOf("") }
    var dial by remember { mutableStateOf("") }
    var keypadOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All calls") }
    var filterMenu by remember { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    val recentScrollConnection = remember(keypadOpen) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f && keypadOpen) keypadOpen = false
                return Offset.Zero
            }
        }
    }
    var contacts by remember { mutableStateOf(emptyList<DialContact>()) }
    var calls by remember { mutableStateOf(emptyList<DialCall>()) }
    var sims by remember { mutableStateOf(emptyList<DialSim>()) }
    var selectedContact by remember { mutableStateOf<DialContactDetail?>(null) }
    var selectedCall by remember { mutableStateOf<DialCall?>(null) }
    var note by remember { mutableStateOf("") }
    var noteDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogText by remember { mutableStateOf("") }
    var keypadSound by remember { mutableStateOf(prefs.getBoolean("keypad_sound",true)) }
    var blocked by remember { mutableStateOf(prefs.getStringSet("blocked_numbers",emptySet())?.toSet() ?: emptySet()) }
    var allHistory by remember { mutableStateOf(false) }
    var favoriteOnly by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }
    var pendingSim by remember { mutableStateOf<DialSim?>(null) }
    var simChooser by remember { mutableStateOf(false) }
    var readContacts by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED) }
    var readCalls by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED) }
    var makeCalls by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED) }
    var readPhone by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED) }

    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        readContacts=result[Manifest.permission.READ_CONTACTS]==true || ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED
        readCalls=result[Manifest.permission.READ_CALL_LOG]==true || ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED
        makeCalls=result[Manifest.permission.CALL_PHONE]==true || ContextCompat.checkSelfPermission(ctx,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED
        readPhone=result[Manifest.permission.READ_PHONE_STATE]==true || ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED
        if(readContacts) contacts=readDialContacts(ctx)
        if(readCalls) calls=readDialCalls(ctx)
        if(pendingNumber.isNotBlank()) { val n=pendingNumber;pendingNumber="";startDialCall(ctx,n,pendingSim) }
    }
    val contactsPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { readContacts=it;if(it) contacts=readDialContacts(ctx) }
    val callsPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { readCalls=it;if(it) calls=readDialCalls(ctx) }

    LaunchedEffect(Unit) {
        if(readContacts) contacts=readDialContacts(ctx)
        if(readCalls) calls=readDialCalls(ctx)
        sims=readDialSims(ctx)
    }
    LaunchedEffect(page,tab) {
        if(page=="main" && tab=="Recents" && readCalls) calls=readDialCalls(ctx)
        if(page=="main" && tab!="Keypad" && readContacts) contacts=readDialContacts(ctx)
    }
    fun callNumber(n:String,sim:DialSim?=null) {
        if(n.isBlank()) return
        pendingSim=sim
        if(!makeCalls) { pendingNumber=n;permissions.launch(arrayOf(Manifest.permission.CALL_PHONE,Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_PHONE_STATE));return }
        startDialCall(ctx,n,sim)
    }
    fun showContact(id:Long) { selectedContact=readDialContact(ctx,id);page="contact";allHistory=false }
    fun showCall(c:DialCall) { selectedCall=c;page="call_detail";allHistory=false }
    val toneScope=rememberCoroutineScope()
    fun key(k:String) { dial+=k;if(keypadSound) toneScope.launch(Dispatchers.Default) { playDialTone(k) } }

    BackHandler(enabled=page!="main" || simChooser || noteDialog || dialogTitle.isNotBlank() || keypadOpen) {
        when {
            simChooser -> simChooser=false
            noteDialog -> noteDialog=false
            dialogTitle.isNotBlank() -> dialogTitle=""
            page=="call_detail" -> page=if(selectedContact!=null) "contact" else "main"
            page!="main" -> page="main"
            keypadOpen -> keypadOpen=false
        }
    }

    val shownCalls=calls.filter {
        (filter=="All calls" || filter=="Missed" && it.type==CallLog.Calls.MISSED_TYPE ||
         filter=="Incoming" && it.type==CallLog.Calls.INCOMING_TYPE || filter=="Outgoing" && it.type==CallLog.Calls.OUTGOING_TYPE) &&
        (it.name.contains(query,true) || it.number.contains(query,true)) && it.number !in blocked
    }
    val shownContacts=contacts.filter { (tab!="Favorites" || it.starred) && (it.name.contains(query,true) || it.number.contains(query,true)) }.distinctBy { it.id }

    Column(Modifier.fillMaxSize().background(bg).padding(WindowInsets.statusBars.asPaddingValues())) {
        when(page) {
            "contact" -> selectedContact?.let { c ->
                ContactPage(c,calls,allHistory,dark,card,field,fg,muted,line,green,blue,red,
                    onBack={page="main"},onCall={callNumber(it)},onSms={openSms(ctx,it)},onVideo={openVideo(ctx,it)},
                    onWhatsApp={openLink(ctx,"https://wa.me/"+it.filter(Char::isDigit))},
                    onTelegram={openLink(ctx,"https://t.me/"+it.filter(Char::isDigit))},
                    onEdit={openEditContact(ctx,c.id)},onFavorite={
                        try { val old=contacts.firstOrNull{it.id==c.id}?.starred==true
                            val v=android.content.ContentValues().apply { put(ContactsContract.Contacts.STARRED,if(old) 0 else 1) }
                            ctx.contentResolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,c.id),v,null,null)
                            contacts=readDialContacts(ctx);selectedContact=readDialContact(ctx,c.id)
                        } catch (_:Exception) {}
                    },onHistory={allHistory=true},onCallDetail={showCall(it)},
                    onRingtone={openAndroidSettings(ctx,android.provider.Settings.ACTION_SOUND_SETTINGS)},
                    onQr={dialogTitle="QR code";dialogText=c.name+"\n"+c.phones.joinToString("\n"){it.first}},
                    onBlock={blocked=blocked+c.phones.map{it.first};prefs.edit().putStringSet("blocked_numbers",blocked).apply();dialogTitle="Blocked";dialogText="This number is hidden from the dialer's recent list."},
                    onDelete={try { ctx.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,c.id),null,null);contacts=readDialContacts(ctx);page="main" } catch (_:Exception) {}},
                    onShare={try { ctx.startActivity(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,c.name+" "+c.phones.joinToString { it.first }).let { Intent.createChooser(it,"Share contact") }) } catch (_:Exception) {}}
                )
            }
            "call_detail" -> selectedCall?.let { c ->
                CallHistoryPage(c,calls.filter{it.number==c.number},fg,muted,card,green,red,
                    onBack={page=if(selectedContact!=null) "contact" else "main"},onCall={callNumber(c.number)},
                    onNote={note=prefs.getString("call_note_"+c.id,"") ?: "";noteDialog=true},
                    onRecordings={dialogTitle="Recorded calls";dialogText="Recording playback depends on the phone manufacturer's dialer and recorder. Android may restrict third-party access."},
                    onDelete={dialogTitle="Delete call history";dialogText="Android protects call-log deletion. Manage or delete call records using the system Phone app."})
            }
            else -> {
                Row(Modifier.fillMaxWidth().padding(start=22.dp,end=12.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(tab=="Recents") "Recents" else tab,Modifier.weight(1f),fontSize=32.sp,fontWeight=FontWeight.Bold,color=fg)
                    Box {
                        IconButton(onClick={overflowMenu=true}) { Icon(Icons.Default.MoreHoriz,null,tint=fg) }
                        DropdownMenu(expanded=overflowMenu,onDismissRequest={overflowMenu=false}) {
                            DropdownMenuItem(text={Text("Phone settings")},leadingIcon={Icon(Icons.Default.Settings,null)},onClick={overflowMenu=false;topMenu=true})
                            DropdownMenuItem(text={Text("Blocked numbers")},leadingIcon={Icon(Icons.Default.Block,null)},onClick={overflowMenu=false;dialogTitle="Blocklist";dialogText=blocked.joinToString("\\n")})
                            DropdownMenuItem(text={Text("System calling settings")},leadingIcon={Icon(Icons.Default.PhoneAndroid,null)},onClick={overflowMenu=false;openAndroidCallSettings(ctx)})
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState=tab,
                        transitionSpec={
                            (fadeIn(tween(190))+slideInHorizontally(tween(190)){ it/14 }) togetherWith
                            (fadeOut(tween(130))+slideOutHorizontally(tween(130)){ -it/14 })
                        },
                        label="dialer-tabs"
                    ) { activeTab ->
                    when(activeTab) {
                        "Recents" -> Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
                            DialSearch(query,{query=it},field,fg,muted)
                            Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                                Box {
                                    Text(filter+"  ⌄",color=muted,fontSize=13.sp,modifier=Modifier.clickable{filterMenu=true}.padding(8.dp))
                                    DropdownMenu(filterMenu,{filterMenu=false}) {
                                        listOf("All calls","Missed","Incoming","Outgoing").forEach { f -> DropdownMenuItem(text={Text(f)},onClick={filter=f;filterMenu=false}) }
                                    }
                                }
                                Spacer(Modifier.weight(1f));Text(shownCalls.size.toString()+" calls",color=muted,fontSize=12.sp)
                            }
                            if(!readCalls) PermissionPanel("Allow call history access to view recent calls","Allow call history",muted) { callsPermission.launch(Manifest.permission.READ_CALL_LOG) }
                            else if(shownCalls.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Text("No recent calls",color=muted) }
                            else LazyColumn(Modifier.weight(1f).nestedScroll(recentScrollConnection),contentPadding=PaddingValues(bottom=if(keypadOpen) 0.dp else 72.dp)) {
                                items(shownCalls,key={it.id}) { c ->
                                    Box(Modifier.animateItem()) {
                                        RecentRow(c,dark,fg,muted,line,green,red,{showCall(c)},{callNumber(c.number)})
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible=keypadOpen,
                                enter=slideInVertically(tween(230)){ it }+fadeIn(tween(200)),
                                exit=slideOutVertically(tween(180)){ it }+fadeOut(tween(150))
                            ) {
                                DialPad(dial,fg,muted,green,onKey={key(it)},onDelete={dial=dial.dropLast(1)},onCall={
                                    if(sims.size>1) simChooser=true else callNumber(dial,sims.firstOrNull())
                                },sims=sims)
                            }
                        }
                        "Contacts","Favorites" -> Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
                            DialSearch(query,{query=it},field,fg,muted)
                            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(tab=="Favorites") "Favorite contacts" else "All contacts",color=muted,fontSize=13.sp)
                                Spacer(Modifier.weight(1f))
                                Text("My groups",color=blue,fontSize=13.sp,modifier=Modifier.clickable {
                                    dialogTitle="My groups";dialogText=readDialGroups(ctx).distinct().joinToString("\n").ifBlank{"No contact groups"}
                                })
                            }
                            if(!readContacts) PermissionPanel("Allow contacts access to see your contacts","Allow contacts",muted) { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }
                            else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=72.dp)) {
                                items(shownContacts,key={it.id}) { c -> ContactRow(c,dark,fg,muted,line,{showContact(c.id)}) }
                            }
                        }
                        "Keypad" -> Column(
                            Modifier.fillMaxSize().padding(horizontal=22.dp),
                            horizontalAlignment=Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(10.dp))
                            if(dial.isNotBlank()) {
                                val digits=dial.filter(Char::isDigit)
                                val matches=contacts.filter { digits.isNotBlank() && it.number.filter(Char::isDigit).contains(digits) }.take(2)
                                AnimatedVisibility(
                                    visible=true,
                                    enter=fadeIn(tween(180))+slideInVertically(tween(180)){ -it/3 },
                                    exit=fadeOut(tween(120))
                                ) {
                                    Surface(
                                        modifier=Modifier.fillMaxWidth().padding(bottom=8.dp),
                                        shape=RoundedCornerShape(18.dp),
                                        color=if(dark) Color(0xFF202023) else Color(0xFFE9E9EF)
                                    ) {
                                        Column(Modifier.padding(horizontal=14.dp,vertical=10.dp)) {
                                            matches.forEach { match ->
                                                Row(
                                                    Modifier.fillMaxWidth().clickable{showContact(match.id)}.padding(vertical=5.dp),
                                                    verticalAlignment=Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Person,null,tint=blue)
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(match.name,color=fg,fontSize=14.sp,fontWeight=FontWeight.Medium)
                                                        Text(match.number,color=muted,fontSize=11.sp)
                                                    }
                                                    Text("Open",color=blue,fontSize=12.sp)
                                                }
                                            }
                                            Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                                Text(
                                                    "Create New Contact",
                                                    Modifier.weight(1f).clickable {
                                                        try {
                                                            ctx.startActivity(Intent(ContactsContract.Intents.Insert.ACTION)
                                                                .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                                                                .putExtra(ContactsContract.Intents.Insert.PHONE,dial))
                                                        } catch (_:Exception) {}
                                                    }.padding(vertical=8.dp),
                                                    color=green,fontSize=12.sp,fontWeight=FontWeight.SemiBold
                                                )
                                                Text(
                                                    "Add to Existing Contact",
                                                    Modifier.weight(1f).clickable {
                                                        try {
                                                            ctx.startActivity(Intent(Intent.ACTION_INSERT_OR_EDIT)
                                                                .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                                                .putExtra(ContactsContract.Intents.Insert.PHONE,dial))
                                                        } catch (_:Exception) {}
                                                    }.padding(vertical=8.dp),
                                                    color=blue,fontSize=12.sp,fontWeight=FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            DialPad(dial,fg,muted,green,onKey={key(it)},onDelete={dial=dial.dropLast(1)},onCall={
                                if(sims.size>1) simChooser=true else callNumber(dial,sims.firstOrNull())
                            },sims=sims)
                            Spacer(Modifier.weight(1f))
                        }
                        "Voicemail" -> Column(
                            Modifier.fillMaxSize().padding(28.dp),
                            horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.Center
                        ) {
                            Icon(Icons.Default.Voicemail,null,tint=muted,modifier=Modifier.size(58.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Voicemail",color=fg,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Voicemail messages provided by your carrier will appear here when supported.",
                                color=muted,fontSize=14.sp,textAlign=TextAlign.Center
                            )
                        }
                        else -> Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {}
                    }
                    }
                    Column(Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible=tab=="Recents" && !keypadOpen,
                            enter=scaleIn(tween(220))+fadeIn(tween(180))+slideInVertically(tween(220)){ it/2 },
                            exit=scaleOut(tween(150))+fadeOut(tween(120))+slideOutVertically(tween(150)){ it/2 }
                        ) { DialFab(green) { keypadOpen=true } }
                    }
                    if(tab=="Contacts") AddFab(green) {
                        try { ctx.startActivity(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)) } catch (_:Exception) {}
                    }
                }
                NavigationBar(containerColor=if(dark) Color(0xFF1C1C1E) else Color(0xFFF1F1F4),contentColor=fg) {
                    listOf(
                        "Favorites" to Icons.Default.Star,
                        "Recents" to Icons.Default.History,
                        "Contacts" to Icons.Default.Contacts,
                        "Keypad" to Icons.Default.Dialpad,
                        "Voicemail" to Icons.Default.Voicemail
                    ).forEach { (label,icon) ->
                        NavigationBarItem(
                            selected=tab==label,
                            onClick={tab=label;query="";keypadOpen=false},
                            icon={Icon(icon,null,modifier=Modifier.size(23.dp))},
                            label={Text(label,fontSize=9.sp,maxLines=1)},
                            colors=NavigationBarItemDefaults.colors(
                                selectedIconColor=blue,
                                selectedTextColor=blue,
                                indicatorColor=Color.Transparent,
                                unselectedIconColor=muted,
                                unselectedTextColor=muted
                            )
                        )
                    }
                }
            }
        }
    }

    if(topMenu) Dialog(onDismissRequest={topMenu=false}) {
        Surface(
            modifier=Modifier.fillMaxWidth().padding(12.dp),
            shape=RoundedCornerShape(30.dp),
            color=(if(dark) Color(0xFF1C1C1E) else Color(0xFFF7F7FA).copy(alpha=.80f)),
            tonalElevation=6.dp
        ) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Phone Settings",color=fg,fontSize=25.sp,fontWeight=FontWeight.Bold)
                        Text("Customize your calling experience",color=muted,fontSize=12.sp)
                    }
                    IconButton(onClick={topMenu=false}) {
                        Icon(Icons.Default.Close,null,tint=muted)
                    }
                }
                Text("SOUNDS & FEEDBACK",color=muted,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Surface(shape=RoundedCornerShape(20.dp),color=card) {
                    Column(Modifier.padding(horizontal=15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
                            Surface(shape=CircleShape,color=if(dark) Color(0xFF343438) else Color(0xFFE9E9EF),modifier=Modifier.size(38.dp)) {
                                Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.VolumeUp,null,tint=blue) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Keypad sounds",color=fg,fontSize=14.sp,fontWeight=FontWeight.Medium)
                                Text("Play tones when dialing numbers",color=muted,fontSize=11.sp)
                            }
                            Switch(checked=keypadSound,onCheckedChange={keypadSound=it;prefs.edit().putBoolean("keypad_sound",it).apply()})
                        }
                        HorizontalDivider(color=line)
                        Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                            Surface(shape=CircleShape,color=if(dark) Color(0xFF343438) else Color(0xFFE9E9EF),modifier=Modifier.size(38.dp)) {
                                Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.DarkMode,null,tint=blue) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Appearance",color=fg,fontSize=14.sp,fontWeight=FontWeight.Medium)
                                Text("Automatically follows system · "+if(dark) "Dark" else "Light",color=muted,fontSize=11.sp)
                            }
                            Icon(Icons.Default.CheckCircle,null,tint=green)
                        }
                    }
                }
                Text("CALL MANAGEMENT",color=muted,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Surface(
                    onClick={
                        topMenu=false
                        dialogTitle="Blocklist"
                        dialogText=blocked.joinToString("\n")
                    },
                    shape=RoundedCornerShape(20.dp),color=card
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=CircleShape,color=if(dark) Color(0xFF343438) else Color(0xFFE9E9EF),modifier=Modifier.size(38.dp)) {
                            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Block,null,tint=red) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Blocked numbers",color=fg,fontSize=14.sp,fontWeight=FontWeight.Medium)
                            Text(blocked.size.toString()+" blocked",color=muted,fontSize=11.sp)
                        }
                        Icon(Icons.Default.ChevronRight,null,tint=muted)
                    }
                }
                Text("SYSTEM",color=muted,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Surface(
                    onClick={topMenu=false;openAndroidCallSettings(ctx)},
                    shape=RoundedCornerShape(20.dp),color=card
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=CircleShape,color=if(dark) Color(0xFF343438) else Color(0xFFE9E9EF),modifier=Modifier.size(38.dp)) {
                            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Settings,null,tint=muted) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("System calling settings",color=fg,fontSize=14.sp,fontWeight=FontWeight.Medium)
                            Text("SIM, forwarding and carrier options",color=muted,fontSize=11.sp)
                        }
                        Icon(Icons.Default.ChevronRight,null,tint=muted)
                    }
                }
                Button(
                    onClick={topMenu=false},
                    modifier=Modifier.fillMaxWidth().height(48.dp),
                    shape=RoundedCornerShape(16.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=blue)
                ) { Text("Done",fontWeight=FontWeight.SemiBold) }
            }
        }
    }
    if(simChooser) IosModal(
        title="Choose a line",
        subtitle="Select which SIM to use for this call",
        dark=dark,
        onDismiss={simChooser=false},
        confirmLabel="Cancel",
        onConfirm={simChooser=false}
    ) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            sims.forEachIndexed { index, sim ->
                Surface(
                    onClick={simChooser=false;callNumber(dial,sim)},
                    color=if(dark) Color(0xFF3A3940) else Color(0xFFF0F0F5),
                    shape=RoundedCornerShape(17.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF3987FF).copy(alpha=.16f)),contentAlignment=Alignment.Center) {
                            Icon(Icons.Default.SimCard,null,tint=Color(0xFF3987FF))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(sim.name,color=fg,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight,null,tint=muted)
                    }
                }
            }
        }
    }
    if(noteDialog && selectedCall!=null) IosModal(
        title="Call note",
        subtitle="Keep a private note with this call",
        dark=dark,
        onDismiss={noteDialog=false},
        confirmLabel="Save",
        onConfirm={prefs.edit().putString("call_note_"+selectedCall!!.id,note).apply();noteDialog=false},
        secondaryLabel="Cancel",
        onSecondary={noteDialog=false}
    ) {
        OutlinedTextField(
            value=note,onValueChange={note=it},
            modifier=Modifier.fillMaxWidth(),
            placeholder={Text("Add a note…")},
            minLines=3,
            shape=RoundedCornerShape(18.dp),
            colors=OutlinedTextFieldDefaults.colors(
                focusedContainerColor=if(dark) Color(0xFF39383F) else Color(0xFFF1F1F5),
                unfocusedContainerColor=if(dark) Color(0xFF39383F) else Color(0xFFF1F1F5),
                focusedBorderColor=Color.Transparent,unfocusedBorderColor=Color.Transparent
            )
        )
    }
    if(dialogTitle.isNotBlank()) IosModal(
        title=dialogTitle,
        subtitle=when(dialogTitle) {
            "My groups" -> "Your contact groups"
            "Blocklist" -> "Numbers hidden from recent calls"
            "QR code" -> "Contact sharing code"
            else -> null
        },
        dark=dark,
        onDismiss={dialogTitle=""},
        confirmLabel="Done",
        onConfirm={
            if(dialogTitle=="Blocklist") {
                blocked=dialogText.lines().map{it.trim()}.filter{it.isNotBlank()}.toSet()
                prefs.edit().putStringSet("blocked_numbers",blocked).apply()
            }
            dialogTitle=""
        }
    ) {
        if(dialogTitle=="Blocklist") {
            OutlinedTextField(
                value=dialogText,onValueChange={dialogText=it},modifier=Modifier.fillMaxWidth(),
                label={Text("One number per line")},minLines=3,shape=RoundedCornerShape(18.dp)
            )
        } else if(dialogTitle=="My groups") {
            val groups=dialogText.lines().filter{it.isNotBlank()}
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                groups.forEach { group ->
                    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen,null,tint=Color(0xFF3987FF),modifier=Modifier.size(19.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(group,color=fg,fontSize=14.sp)
                    }
                }
            }
        } else {
            Text(dialogText,color=fg.copy(alpha=.88f),fontSize=14.sp,lineHeight=21.sp)
        }
    }
}

@Composable
private fun IosModal(
    title:String,
    subtitle:String?,
    dark:Boolean,
    onDismiss:()->Unit,
    confirmLabel:String,
    onConfirm:()->Unit,
    secondaryLabel:String?=null,
    onSecondary:(()->Unit)?=null,
    content:@Composable ColumnScope.()->Unit
) {
    val fg=if(dark) Color(0xFFF5F5F7) else Color(0xFF17171B)
    val muted=if(dark) Color(0xFFB0AFB7) else Color(0xFF777780)
    val panel=if(dark) Color(0xE82B2A30) else Color(0xEFFFFFFF)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val view=LocalView.current
        val window=(view.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(.34f)
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            if(android.os.Build.VERSION.SDK_INT>=31) window?.setBackgroundBlurRadius(34)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.34f)).clickable(onClick=onDismiss),contentAlignment=Alignment.Center) {
            AnimatedVisibility(
                visible=true,
                enter=fadeIn(tween(180))+scaleIn(tween(230,delayMillis=10),initialScale=.92f),
                exit=fadeOut(tween(120))+scaleOut(tween(120))
            ) {
                Surface(
                    modifier=Modifier.fillMaxWidth(.88f).clickable(enabled=false){}.padding(0.dp),
                    shape=RoundedCornerShape(30.dp),
                    color=panel,
                    tonalElevation=0.dp,
                    shadowElevation=24.dp
                ) {
                    Column(Modifier.padding(start=22.dp,end=22.dp,top=23.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                        Text(title,color=fg,fontSize=25.sp,fontWeight=FontWeight.SemiBold)
                        if(!subtitle.isNullOrBlank()) Text(subtitle,color=muted,fontSize=13.sp)
                        content()
                        Row(Modifier.fillMaxWidth().padding(top=5.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                            if(secondaryLabel!=null && onSecondary!=null) {
                                TextButton(onClick=onSecondary) { Text(secondaryLabel,color=Color(0xFF3987FF),fontWeight=FontWeight.SemiBold) }
                                Spacer(Modifier.width(5.dp))
                            }
                            TextButton(onClick=onConfirm) { Text(confirmLabel,color=Color(0xFF3987FF),fontWeight=FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactAvatar(photoUri:String?,name:String,size:androidx.compose.ui.unit.Dp,dark:Boolean,fg:Color,fontSize:androidx.compose.ui.unit.TextUnit) {
    val ctx=LocalContext.current
    val bitmap=remember(photoUri) { try { photoUri?.let { ctx.contentResolver.openInputStream(Uri.parse(it))?.use { stream -> BitmapFactory.decodeStream(stream) } } } catch (_:Exception) { null } }
    Box(Modifier.size(size).clip(CircleShape).background(if(dark) Color(0xFF343438) else Color(0xFFE7E7EC)),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap.asImageBitmap(),contentDescription=name,modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
        else Icon(Icons.Default.Person,null,tint=fg.copy(alpha=.86f),modifier=Modifier.size(size*.58f))
    }
}
private fun copyPhoneNumber(ctx:Context,number:String) {
    if(number.isBlank()) return
    try { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Phone number",number)) } catch (_:Exception) {}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactPage(c:DialContactDetail,calls:List<DialCall>,allHistory:Boolean,dark:Boolean,card:Color,field:Color,fg:Color,muted:Color,line:Color,green:Color,blue:Color,red:Color,
    onBack:()->Unit,onCall:(String)->Unit,onSms:(String)->Unit,onVideo:(String)->Unit,onWhatsApp:(String)->Unit,onTelegram:(String)->Unit,onEdit:()->Unit,onFavorite:()->Unit,onHistory:()->Unit,onCallDetail:(DialCall)->Unit,onRingtone:()->Unit,onQr:()->Unit,onBlock:()->Unit,onDelete:()->Unit,onShare:()->Unit) {
    val ctx=LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val history=calls.filter { call -> c.phones.any{it.first==call.number} }
    val panel=if(dark) Color(0xFF242428) else Color.White
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBackIosNew,null,tint=fg) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick=onFavorite) { Icon(if(c.starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(c.starred) Color(0xFFFF375F) else fg) }
            IconButton(onClick=onEdit) { Icon(Icons.Default.Edit,null,tint=fg) }
            Box {
                IconButton(onClick={menu=true}) { Icon(Icons.Default.MoreHoriz,null,tint=fg) }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("Copy number")},leadingIcon={Icon(Icons.Default.ContentCopy,null)},onClick={menu=false;copyPhoneNumber(ctx,c.phones.firstOrNull()?.first.orEmpty())})
                    DropdownMenuItem(text={Text(if(c.starred) "Remove from Favorites" else "Add to Favorites")},leadingIcon={Icon(Icons.Default.Favorite,null)},onClick={menu=false;onFavorite()})
                    DropdownMenuItem(text={Text("Share contact")},leadingIcon={Icon(Icons.Default.Share,null)},onClick={menu=false;onShare()})
                    DropdownMenuItem(text={Text("Block contact")},leadingIcon={Icon(Icons.Default.Block,null)},onClick={menu=false;onBlock()})
                    DropdownMenuItem(text={Text("Delete contact")},leadingIcon={Icon(Icons.Default.Delete,null)},onClick={menu=false;onDelete()})
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=4.dp)) {
            item {
                AnimatedVisibility(visible=true,enter=fadeIn(tween(260))+scaleIn(tween(260,delayMillis=30))) {
                    Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        ContactAvatar(c.photoUri,c.name,104.dp,dark,fg,43.sp)
                        Spacer(Modifier.height(14.dp))
                        Text(c.name,fontSize=28.sp,fontWeight=FontWeight.SemiBold,color=fg,textAlign=TextAlign.Center)
                        Text(c.phones.firstOrNull()?.first ?: "No phone number",fontSize=14.sp,color=muted,modifier=Modifier.padding(top=4.dp))
                        Row(Modifier.fillMaxWidth().padding(top=22.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                            ContactQuickAction(Icons.Default.Call,"call",green){c.phones.firstOrNull()?.first?.let(onCall)}
                            ContactQuickAction(Icons.Default.Message,"message",blue){c.phones.firstOrNull()?.first?.let(onSms)}
                            ContactQuickAction(Icons.Default.Videocam,"video",blue){c.phones.firstOrNull()?.first?.let(onVideo)}
                            ContactQuickAction(Icons.Default.MailOutline,"mail",muted){ }
                        }
                    }
                }
                ContactSectionCard(panel,dark) {
                    c.phones.forEachIndexed { index,p ->
                        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).combinedClickable(onClick={},onLongClick={copyPhoneNumber(ctx,p.first)})) {
                                Text(p.first,color=blue,fontSize=16.sp,fontWeight=FontWeight.Medium)
                                Text(p.second.ifBlank{"mobile"}.replaceFirstChar{it.uppercase()},color=muted,fontSize=12.sp,modifier=Modifier.padding(top=3.dp))
                            }
                            IconButton(onClick={onCall(p.first)}) { Icon(Icons.Default.Call,null,tint=green) }
                            IconButton(onClick={onSms(p.first)}) { Icon(Icons.Default.ChatBubbleOutline,null,tint=green) }
                        }
                        if(index<c.phones.lastIndex) HorizontalDivider(color=line)
                    }
                    c.emails.forEach { (value,type) ->
                        HorizontalDivider(color=line)
                        Column(Modifier.padding(vertical=10.dp)) { Text(value,color=blue,fontSize=15.sp);Text(type,color=muted,fontSize=12.sp) }
                    }
                    c.addresses.forEach { (value,type) ->
                        HorizontalDivider(color=line)
                        Column(Modifier.padding(vertical=10.dp)) { Text(value,color=fg,fontSize=15.sp);Text(type,color=muted,fontSize=12.sp) }
                    }
                    c.birthday?.let { value ->
                        HorizontalDivider(color=line)
                        Column(Modifier.padding(vertical=10.dp)) { Text(value,color=fg,fontSize=15.sp);Text("Birthday",color=muted,fontSize=12.sp) }
                    }
                }
                Text("CONNECTED APPS",color=muted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.sp,modifier=Modifier.padding(start=8.dp,top=22.dp,bottom=9.dp))
                ContactSectionCard(panel,dark) {
                    ContactAction("WhatsApp",Icons.Default.Chat,green,fg){c.phones.firstOrNull()?.first?.let(onWhatsApp)}
                    HorizontalDivider(color=line)
                    ContactAction("Telegram",Icons.Default.Send,blue,fg){c.phones.firstOrNull()?.first?.let(onTelegram)}
                }
                Row(Modifier.fillMaxWidth().padding(start=8.dp,top=23.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("RECENT CALLS",color=muted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.sp,modifier=Modifier.weight(1f))
                    if(history.size>3 && !allHistory) Text("See all",color=blue,fontSize=13.sp,modifier=Modifier.clickable(onClick=onHistory))
                }
                ContactSectionCard(panel,dark) {
                    (if(allHistory) history else history.take(3)).forEachIndexed { index,call ->
                        Row(Modifier.fillMaxWidth().clickable{onCallDetail(call)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(37.dp).clip(CircleShape).background(if(call.type==CallLog.Calls.MISSED_TYPE) red.copy(alpha=.13f) else green.copy(alpha=.13f)),contentAlignment=Alignment.Center) {
                                Icon(
                                    when(call.type) { CallLog.Calls.MISSED_TYPE->Icons.Default.CallMissed;CallLog.Calls.OUTGOING_TYPE->Icons.Default.CallMade;else->Icons.Default.CallReceived },
                                    null,tint=if(call.type==CallLog.Calls.MISSED_TYPE) red else green,modifier=Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(callSummary(call),color=fg,fontSize=14.sp,fontWeight=FontWeight.Medium)
                                Text(SimpleDateFormat("MMM d · h:mm a",Locale.getDefault()).format(Date(call.date)),color=muted,fontSize=12.sp,modifier=Modifier.padding(top=3.dp))
                            }
                            Icon(Icons.Default.ChevronRight,null,tint=muted)
                        }
                        if(index<(if(allHistory) history.size else minOf(history.size,3))-1) HorizontalDivider(color=line)
                    }
                    if(history.isEmpty()) Text("No call history",color=muted,fontSize=14.sp,modifier=Modifier.padding(vertical=12.dp))
                }
                Text("MORE",color=muted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.sp,modifier=Modifier.padding(start=8.dp,top=23.dp,bottom=9.dp))
                ContactSectionCard(panel,dark) {
                    ContactAction("Default ringtone",Icons.Default.MusicNote,muted,fg,onRingtone)
                    HorizontalDivider(color=line)
                    ContactAction("QR code",Icons.Default.QrCode,muted,fg,onQr)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun ContactQuickAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,tint:Color,onClick:()->Unit) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(66.dp).clickable(onClick=onClick)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(tint.copy(alpha=.14f)),contentAlignment=Alignment.Center) {
            Icon(icon,null,tint=tint,modifier=Modifier.size(23.dp))
        }
        Text(label,color=tint,fontSize=12.sp,modifier=Modifier.padding(top=7.dp))
    }
}

@Composable
private fun ContactSectionCard(panel:Color,dark:Boolean,content:@Composable ColumnScope.()->Unit) {
    Surface(color=panel,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal=16.dp,vertical=5.dp),content=content)
    }
}

@Composable
private fun CallHistoryPage(call:DialCall,history:List<DialCall>,fg:Color,muted:Color,card:Color,green:Color,red:Color,onBack:()->Unit,onCall:()->Unit,onNote:()->Unit,onRecordings:()->Unit,onDelete:()->Unit) {
    val dark=fg.red>.5f
    val panel=if(dark) Color(0xFF242428) else Color.White
    val callType=when(call.type) {
        CallLog.Calls.MISSED_TYPE->"Missed call"
        CallLog.Calls.OUTGOING_TYPE->"Outgoing call"
        CallLog.Calls.INCOMING_TYPE->"Incoming call"
        CallLog.Calls.REJECTED_TYPE->"Declined call"
        else->"Call"
    }
    val duration=if(call.duration>0) (call.duration/60).toString()+" min "+(call.duration%60)+" sec" else "Not connected"
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBackIosNew,null,tint=fg) }
            Text("Call details",Modifier.weight(1f),fontSize=18.sp,color=fg,textAlign=TextAlign.Center,fontWeight=FontWeight.SemiBold)
            IconButton(onClick=onCall) { Icon(Icons.Default.Phone,null,tint=green) }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp)) {
            item {
                AnimatedVisibility(visible=true,enter=fadeIn(tween(240))+slideInVertically(tween(240)){it/5}) {
                    Column(Modifier.fillMaxWidth().padding(top=16.dp,bottom=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(88.dp).clip(CircleShape).background(if(call.type==CallLog.Calls.MISSED_TYPE) red.copy(alpha=.14f) else green.copy(alpha=.14f)),contentAlignment=Alignment.Center) {
                            Icon(
                                when(call.type) { CallLog.Calls.MISSED_TYPE->Icons.Default.CallMissed;CallLog.Calls.OUTGOING_TYPE->Icons.Default.CallMade;else->Icons.Default.CallReceived },
                                null,tint=if(call.type==CallLog.Calls.MISSED_TYPE) red else green,modifier=Modifier.size(37.dp)
                            )
                        }
                        Text(call.name.ifBlank{call.number.ifBlank{"Unknown caller"}},color=fg,fontSize=25.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=15.dp))
                        Text(call.number,color=muted,fontSize=15.sp,modifier=Modifier.padding(top=5.dp))
                        Text(callType,color=if(call.type==CallLog.Calls.MISSED_TYPE) red else green,fontSize=13.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp))
                    }
                }
                ContactSectionCard(panel,dark) {
                    CallInfoRow(Icons.Default.CalendarToday,"Date",SimpleDateFormat("EEEE, MMM d, yyyy",Locale.getDefault()).format(Date(call.date)),fg,muted)
                    HorizontalDivider(color=muted.copy(alpha=.18f))
                    CallInfoRow(Icons.Default.Schedule,"Time",SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(call.date)),fg,muted)
                    HorizontalDivider(color=muted.copy(alpha=.18f))
                    CallInfoRow(Icons.Default.Timer,"Duration",duration,fg,muted)
                    HorizontalDivider(color=muted.copy(alpha=.18f))
                    CallInfoRow(Icons.Default.SimCard,"SIM",call.account.ifBlank{"Default line"},fg,muted)
                }
                Text("CALL HISTORY",color=muted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.sp,modifier=Modifier.padding(start=8.dp,top=24.dp,bottom=9.dp))
            }
            items(history.sortedByDescending{it.date},key={it.id}) { item ->
                Surface(color=panel,shape=RoundedCornerShape(17.dp),modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(
                            when(item.type) { CallLog.Calls.MISSED_TYPE->Icons.Default.CallMissed;CallLog.Calls.OUTGOING_TYPE->Icons.Default.CallMade;else->Icons.Default.CallReceived },
                            null,tint=if(item.type==CallLog.Calls.MISSED_TYPE) red else green
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(when(item.type){CallLog.Calls.MISSED_TYPE->"Missed";CallLog.Calls.OUTGOING_TYPE->"Outgoing";else->"Incoming"},color=fg,fontSize=14.sp,fontWeight=FontWeight.Medium)
                            Text(SimpleDateFormat("MMM d, h:mm a",Locale.getDefault()).format(Date(item.date)),color=muted,fontSize=12.sp)
                        }
                        Text(if(item.duration>0) item.duration.toString()+"s" else "—",color=muted,fontSize=12.sp)
                    }
                }
            }
        }
        Surface(color=if(dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),shadowElevation=12.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                CallDetailAction(Icons.Default.Voicemail,"Recordings",muted,onRecordings)
                CallDetailAction(Icons.Default.Notes,"Call notes",Color(0xFF3987FF),onNote)
                CallDetailAction(Icons.Default.Delete,"Delete",red,onDelete)
            }
        }
    }
}

@Composable
private fun CallInfoRow(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,value:String,fg:Color,muted:Color) {
    Row(Modifier.fillMaxWidth().padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=muted,modifier=Modifier.size(19.dp))
        Spacer(Modifier.width(13.dp))
        Text(label,color=muted,fontSize=13.sp,modifier=Modifier.weight(1f))
        Text(value,color=fg,fontSize=13.sp,fontWeight=FontWeight.Medium)
    }
}

@Composable
private fun CallDetailAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,tint:Color,onClick:()->Unit) {
    Column(Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick=onClick).padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(icon,null,tint=tint,modifier=Modifier.size(22.dp))
        Text(label,color=tint,fontSize=11.sp,modifier=Modifier.padding(top=5.dp))
    }
}

@Composable
private fun CallSettingsPage(fg:Color,muted:Color,card:Color,line:Color,onBack:()->Unit,onItem:(String)->Unit) {
    val sections=listOf(listOf("Mobile networks","Call recording","Call-forwarding settings","Call waiting","Incoming call settings","Identify unknown numbers","Blocklist","Location","Auto-answer","Advanced settings"),listOf("Privacy","Feedback"))
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBack,null,tint=fg) }
            Text("Call settings",fontSize=27.sp,color=fg,modifier=Modifier.padding(start=12.dp))
        }
        LazyColumn(contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp)) {
            sections.forEach { section -> item {
                Card(colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(bottom=12.dp)) {
                    Column(Modifier.padding(horizontal=14.dp)) {
                        section.forEach { label ->
                            Row(Modifier.fillMaxWidth().clickable{onItem(label)}.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(label,color=fg,fontSize=15.sp)
                                    if(label=="Call waiting") Text("During a call, notify me of other incoming calls",color=muted,fontSize=11.sp)
                                    if(label=="Location") Text("Show contact location, don't auto-dial country code",color=muted,fontSize=11.sp)
                                }
                                if(label=="Call recording" || label=="Auto-answer") Text("Off",color=muted,fontSize=12.sp)
                                Icon(Icons.Default.ChevronRight,null,tint=muted)
                            }
                            if(label!=section.last()) HorizontalDivider(color=line)
                        }
                    }
                }
            } }
        }
    }
}

@Composable
private fun RecentRow(c:DialCall,dark:Boolean,fg:Color,muted:Color,line:Color,green:Color,red:Color,onOpen:()->Unit,onCall:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if(dark) Color(0xFF343438) else Color(0xFFE7E7EC)),contentAlignment=Alignment.Center) {
            Icon(if(c.type==CallLog.Calls.MISSED_TYPE) Icons.Default.CallMissed else if(c.type==CallLog.Calls.OUTGOING_TYPE) Icons.Default.CallMade else Icons.Default.CallReceived,null,tint=if(c.type==CallLog.Calls.MISSED_TYPE) red else green)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name.ifBlank{c.number.ifBlank{"Unknown"}},color=if(c.type==CallLog.Calls.MISSED_TYPE) red else fg,fontSize=15.sp,fontWeight=FontWeight.Medium)
            Text(c.number+"  ·  "+SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(c.date))+"  "+c.account,color=muted,fontSize=12.sp)
        }
        IconButton(onClick=onOpen) { Icon(Icons.Default.ChevronRight,null,tint=muted) }
        IconButton(onClick=onCall) { Icon(Icons.Default.Call,null,tint=green) }
    }
    HorizontalDivider(color=line)
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(c:DialContact,dark:Boolean,fg:Color,muted:Color,line:Color,onClick:()->Unit) {
    val ctx=LocalContext.current
    Row(Modifier.fillMaxWidth().combinedClickable(onClick=onClick,onLongClick={copyPhoneNumber(ctx,c.number)}).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        ContactAvatar(c.photoUri,c.name,44.dp,dark,muted,18.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(c.name,color=fg,fontSize=15.sp,fontWeight=FontWeight.Medium);Text(c.number,color=muted,fontSize=12.sp) }
        if(c.starred) Icon(Icons.Default.Star,null,tint=Color(0xFFFFB52E),modifier=Modifier.size(18.dp))
        Icon(Icons.Default.ChevronRight,null,tint=muted)
    }
    HorizontalDivider(color=line)
}
@Composable
private fun DialSearch(value:String,onChange:(String)->Unit,field:Color,fg:Color,muted:Color) {
    OutlinedTextField(value=value,onValueChange=onChange,modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search contacts",color=muted)},
        leadingIcon={Icon(Icons.Default.Search,null,tint=muted)},trailingIcon={if(value.isNotBlank()) IconButton(onClick={onChange("")}) { Icon(Icons.Default.Close,null,tint=muted) }},
        shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=field,unfocusedContainerColor=field,focusedBorderColor=Color.Transparent,unfocusedBorderColor=Color.Transparent,focusedTextColor=fg,unfocusedTextColor=fg))
}
@Composable
private fun PermissionPanel(message:String,button:String,muted:Color,onClick:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Icon(Icons.Default.Security,null,tint=muted,modifier=Modifier.size(46.dp));Text(message,color=muted,textAlign=TextAlign.Center,modifier=Modifier.padding(14.dp))
        Button(onClick=onClick) { Text(button) }
    }
}
@Composable
private fun DialPad(number:String,fg:Color,muted:Color,green:Color,onKey:(String)->Unit,onDelete:()->Unit,onCall:()->Unit,sims:List<DialSim>) {
    val keyBg=if(fg.red>0.5f) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    Column(
        Modifier.fillMaxWidth().padding(top=8.dp,bottom=8.dp),
        horizontalAlignment=Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible=number.isNotBlank(),
            enter=fadeIn(tween(160))+slideInVertically(tween(160)){it/3},
            exit=fadeOut(tween(110))+slideOutVertically(tween(110)){it/3}
        ) {
            Row(
                Modifier.fillMaxWidth().height(54.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                Text(
                    number,
                    Modifier.weight(1f),
                    fontSize=29.sp,
                    color=green,
                    textAlign=TextAlign.Center,
                    maxLines=1
                )
                Box(
                    Modifier.size(48.dp).clip(CircleShape).pointerInput(Unit) {
                        coroutineScope {
                            val gestureScope=this
                            awaitEachGesture {
                                val down=awaitFirstDown(requireUnconsumed=false)
                                val longPress=awaitLongPressOrCancellation(down.id)
                                if(longPress==null) {
                                    if(number.isNotEmpty()) onDelete()
                                } else {
                                    val repeatJob=gestureScope.launch {
                                        while(true) {
                                            onDelete()
                                            delay(85)
                                        }
                                    }
                                    waitForUpOrCancellation()
                                    repeatJob.cancel()
                                }
                            }
                        }
                    },
                    contentAlignment=Alignment.Center
                ) { Icon(Icons.Default.Backspace,null,tint=muted,modifier=Modifier.size(22.dp)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        listOf("1","2","3","4","5","6","7","8","9","*","0","#").chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical=5.dp),
                horizontalArrangement=Arrangement.SpaceEvenly,
                verticalAlignment=Alignment.CenterVertically
            ) {
                row.forEach { k ->
                    Column(
                        Modifier.size(70.dp).clip(CircleShape).background(keyBg)
                            .clickable { onKey(k) },
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Center
                    ) {
                        Text(k,fontSize=29.sp,color=fg,fontWeight=FontWeight.Normal)
                        Text(
                            when(k) {
                                "2"->"ABC";"3"->"DEF";"4"->"GHI";"5"->"JKL";"6"->"MNO"
                                "7"->"PQRS";"8"->"TUV";"9"->"WXYZ";"0"->"+"
                                else->""
                            },
                            fontSize=8.sp,color=if(fg.red>0.5f) Color(0xFFB7B7BC) else Color(0xFF66666D),
                            letterSpacing=1.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Surface(
            onClick=onCall,
            shape=CircleShape,
            color=green,
            shadowElevation=8.dp,
            modifier=Modifier.size(76.dp)
        ) {
            Box(contentAlignment=Alignment.Center) {
                Icon(Icons.Default.Call,null,tint=Color.White,modifier=Modifier.size(31.dp))
            }
        }
        if(sims.size>1) {
            Text(
                "Choose a SIM after tapping call",
                color=muted,fontSize=10.sp,
                modifier=Modifier.padding(top=7.dp)
            )
        }
    }
}
@Composable
private fun DialFab(green:Color,onClick:()->Unit) {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {
        Surface(onClick=onClick,shape=CircleShape,color=green,shadowElevation=8.dp,modifier=Modifier.padding(bottom=12.dp).size(60.dp)) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Dialpad,null,tint=Color.White,modifier=Modifier.size(26.dp)) }
        }
    }
}
@Composable
private fun AddFab(green:Color,onClick:()->Unit) {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomEnd) {
        Surface(onClick=onClick,shape=CircleShape,color=green,shadowElevation=8.dp,modifier=Modifier.padding(end=18.dp,bottom=14.dp).size(56.dp)) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Add,null,tint=Color.White,modifier=Modifier.size(28.dp)) }
        }
    }
}
@Composable
private fun ContactAction(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tint:Color,fg:Color,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),color=fg,fontSize=15.sp);Icon(icon,null,tint=tint)
    }
}
private fun callSummary(c:DialCall):String {
    val type=when(c.type){CallLog.Calls.INCOMING_TYPE->"Incoming";CallLog.Calls.OUTGOING_TYPE->"Outgoing";CallLog.Calls.MISSED_TYPE->"Missed";CallLog.Calls.REJECTED_TYPE->"Rejected";else->"Call"}
    val duration=if(c.duration>0) (c.duration/60).toString()+"m "+(c.duration%60)+"s" else "Didn't connect"
    return type+" · "+duration+" · "+c.account
}
private val sharedDialTone:ToneGenerator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ToneGenerator(AudioManager.STREAM_DTMF,70) }
private fun playDialTone(key:String) {
    try {
        val tone=when(key){"*"->ToneGenerator.TONE_DTMF_S;"#"->ToneGenerator.TONE_DTMF_P;else->ToneGenerator.TONE_DTMF_0+(key.firstOrNull()?.digitToIntOrNull()?:0)}
        sharedDialTone.startTone(tone,90)
    } catch (_:Exception) {}
}
private fun startDialCall(ctx:Context,n:String,sim:DialSim?) {
    val intent=Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(n)))
    if(sim?.accountId!=null) try {
        val telecom=ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecom.callCapablePhoneAccounts.firstOrNull{it.id==sim.accountId}?.let{intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,it)}
    } catch (_:Exception) {}
    try { ctx.startActivity(intent) } catch (_:SecurityException) { try{ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(n))))}catch(_:Exception){} } catch (_:Exception) {}
}
private fun readDialSims(ctx:Context):List<DialSim> {
    val out=mutableListOf<DialSim>()
    try {
        val sm=ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val tm=ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        sm.activeSubscriptionInfoList.orEmpty().forEach { info ->
            val handle=tm.callCapablePhoneAccounts.firstOrNull{it.id==info.subscriptionId.toString()}
            out.add(DialSim(info.subscriptionId,info.displayName?.toString()?.ifBlank{"SIM "+(info.simSlotIndex+1)} ?: "SIM "+(info.simSlotIndex+1),handle?.id))
        }
    } catch (_:Exception) {}
    return out
}
private fun readDialCalls(ctx:Context):List<DialCall> {
    if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED) return emptyList()
    val out=mutableListOf<DialCall>()
    try {
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI,arrayOf(CallLog.Calls._ID,CallLog.Calls.CACHED_NAME,CallLog.Calls.NUMBER,CallLog.Calls.TYPE,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.PHONE_ACCOUNT_ID),null,null,CallLog.Calls.DATE+" DESC")?.use { c ->
            val ix=(0..6).map{c.getColumnIndex(c.getColumnName(it))}
            while(c.moveToNext()) {
                val account=c.getString(ix[6]).orEmpty()
                out.add(DialCall(c.getLong(ix[0]),c.getString(ix[1]).orEmpty(),c.getString(ix[2]).orEmpty(),c.getInt(ix[3]),c.getLong(ix[4]),c.getLong(ix[5]),if(account.isBlank()) "" else "SIM"))
            }
        }
    } catch (_:Exception) {}
    return out
}
private fun readDialContacts(ctx:Context):List<DialContact> {
    if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) return emptyList()
    val out=mutableListOf<DialContact>()
    try {
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.Contacts.STARRED,ContactsContract.Contacts.PHOTO_URI),null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);val name=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val num=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);val star=c.getColumnIndex(ContactsContract.Contacts.STARRED);val photo=c.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            while(c.moveToNext()) out.add(DialContact(c.getLong(id),c.getString(name).orEmpty(),c.getString(num).orEmpty(),c.getInt(star)==1,c.getString(photo)))
        }
    } catch (_:Exception) {}
    return out.distinctBy{it.id}
}
private fun readDialContact(ctx:Context,id:Long):DialContactDetail {
    var name="Contact";var photoUri:String?=null;var starred=false;val phones=mutableListOf<Pair<String,String>>();val emails=mutableListOf<Pair<String,String>>();val addresses=mutableListOf<Pair<String,String>>();var birthday:String?=null
    try {
        ctx.contentResolver.query(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id),arrayOf(ContactsContract.Contacts.DISPLAY_NAME,ContactsContract.Contacts.PHOTO_URI,ContactsContract.Contacts.STARRED),null,null,null)?.use{if(it.moveToFirst()){name=it.getString(0).orEmpty();photoUri=it.getString(1);starred=it.getInt(2)==1}}
        ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI,arrayOf(ContactsContract.Data.MIMETYPE,ContactsContract.Data.DATA1,ContactsContract.Data.DATA2,ContactsContract.Data.DATA3),ContactsContract.Data.CONTACT_ID+"=?",arrayOf(id.toString()),null)?.use { c ->
            while(c.moveToNext()) {
                val mime=c.getString(0);val value=c.getString(1).orEmpty();val type=c.getInt(2)
                val label=c.getString(3)?.takeIf{it.isNotBlank()} ?: "Other"
                when(mime) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> if(value.isNotBlank()) phones.add(value to label)
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> if(value.isNotBlank()) emails.add(value to label)
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> if(value.isNotBlank()) addresses.add(value to label)
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> if(type==ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) birthday=value
                }
            }
        }
    } catch (_:Exception) {}
    return DialContactDetail(id,name,phones.distinctBy{it.first.filter(Char::isDigit)},emails.distinctBy{it.first.lowercase(Locale.getDefault())},addresses.distinctBy{it.first},birthday,photoUri,starred)
}
private fun readDialGroups(ctx:Context):List<String> {
    val out=mutableListOf<String>()
    try { ctx.contentResolver.query(ContactsContract.Groups.CONTENT_URI,arrayOf(ContactsContract.Groups.TITLE),ContactsContract.Groups.TITLE+" IS NOT NULL AND "+ContactsContract.Groups.TITLE+" != ?",arrayOf(""),ContactsContract.Groups.TITLE+" COLLATE NOCASE ASC")?.use{while(it.moveToNext())out.add(it.getString(0).orEmpty())} } catch (_:Exception) {}
    return out.filter { it.isNotBlank() }.distinctBy { it.trim().lowercase(Locale.getDefault()) }
}
private fun openEditContact(ctx:Context,id:Long) { try{ctx.startActivity(Intent(Intent.ACTION_EDIT,ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id)))}catch(_:Exception){} }
private fun openSms(ctx:Context,n:String) { try{ctx.startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(n))))}catch(_:Exception){} }
private fun openVideo(ctx:Context,n:String) { try{ctx.startActivity(Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(n))).putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE",3))}catch(_:Exception){try{ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(n))))}catch(_:Exception){}} }
private fun openLink(ctx:Context,url:String) { try{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){} }
private fun openAndroidSettings(ctx:Context,action:String) { try{ctx.startActivity(Intent(action))}catch(_:Exception){openAndroidCallSettings(ctx)} }
private fun openAndroidCallSettings(ctx:Context) { try{ctx.startActivity(Intent("android.telephony.action.CALL_SETTINGS"))}catch(_:Exception){try{ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))}catch(_:Exception){}} }
