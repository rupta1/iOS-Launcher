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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DialContact(val id:Long,val name:String,val number:String,val starred:Boolean)
private data class DialCall(val id:Long,val name:String,val number:String,val type:Int,val date:Long,val duration:Long,val account:String)
private data class DialSim(val id:Int,val name:String,val accountId:String?)
private data class DialContactDetail(val id:Long,val name:String,val phones:List<Pair<String,String>>,val emails:List<Pair<String,String>>,val addresses:List<Pair<String,String>>,val birthday:String?)

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
    fun key(k:String) { dial+=k;if(keypadSound) playDialTone(k) }

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
                        IconButton(onClick={topMenu=true}) { Icon(Icons.Default.Settings,null,tint=fg) }
                        // Settings are presented in the iOS-style sheet below the main content.
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
            color=if(dark) Color(0xFF1C1C1E) else Color(0xFFF7F7FA),
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
    if(simChooser) AlertDialog(onDismissRequest={simChooser=false},title={Text("Choose SIM")},text={
        Column { sims.forEach { sim -> Row(Modifier.fillMaxWidth().clickable{simChooser=false;callNumber(dial,sim)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.SimCard,null,tint=green);Spacer(Modifier.width(12.dp));Text(sim.name)
        } } }
    },confirmButton={TextButton(onClick={simChooser=false}) { Text("Cancel") }})
    if(noteDialog && selectedCall!=null) AlertDialog(onDismissRequest={noteDialog=false},title={Text("Call note")},text={
        OutlinedTextField(value=note,onValueChange={note=it},placeholder={Text("Add a note")},minLines=3)
    },confirmButton={TextButton(onClick={prefs.edit().putString("call_note_"+selectedCall!!.id,note).apply();noteDialog=false}) { Text("Save") }},dismissButton={TextButton(onClick={noteDialog=false}) { Text("Cancel") }})
    if(dialogTitle.isNotBlank()) AlertDialog(onDismissRequest={dialogTitle=""},title={Text(dialogTitle)},text={
        if(dialogTitle=="Blocklist") OutlinedTextField(value=dialogText,onValueChange={dialogText=it},label={Text("One number per line")},minLines=3) else Text(dialogText)
    },confirmButton={TextButton(onClick={
        if(dialogTitle=="Blocklist") { blocked=dialogText.lines().map{it.trim()}.filter{it.isNotBlank()}.toSet();prefs.edit().putStringSet("blocked_numbers",blocked).apply() }
        dialogTitle=""
    }) { Text("Done") }})
}

@Composable
private fun ContactPage(c:DialContactDetail,calls:List<DialCall>,allHistory:Boolean,dark:Boolean,card:Color,field:Color,fg:Color,muted:Color,line:Color,green:Color,blue:Color,red:Color,
    onBack:()->Unit,onCall:(String)->Unit,onSms:(String)->Unit,onVideo:(String)->Unit,onWhatsApp:(String)->Unit,onTelegram:(String)->Unit,onEdit:()->Unit,onFavorite:()->Unit,onHistory:()->Unit,onCallDetail:(DialCall)->Unit,onRingtone:()->Unit,onQr:()->Unit,onBlock:()->Unit,onDelete:()->Unit,onShare:()->Unit) {
    var menu by remember { mutableStateOf(false) }
    val history=calls.filter { call -> c.phones.any{it.first==call.number} }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBack,null,tint=fg) }
            Text(c.name,Modifier.weight(1f),fontSize=20.sp,color=fg,textAlign=TextAlign.Center,fontWeight=FontWeight.SemiBold)
            IconButton(onClick=onFavorite) { Icon(Icons.Default.FavoriteBorder,null,tint=fg) }
            IconButton(onClick=onEdit) { Icon(Icons.Default.Edit,null,tint=fg) }
            Box { IconButton(onClick={menu=true}) { Icon(Icons.Default.MoreVert,null,tint=fg) }
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text("Place on Home screen")},onClick={menu=false})
                    DropdownMenuItem(text={Text("Delete contact")},onClick={menu=false;onDelete()})
                    DropdownMenuItem(text={Text("Block")},onClick={menu=false;onBlock()})
                    DropdownMenuItem(text={Text("Share")},onClick={menu=false;onShare()})
                    DropdownMenuItem(text={Text("Set default SIM for calls")},onClick={menu=false})
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp)) {
            item {
                Text(c.name,fontSize=27.sp,color=fg,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(vertical=14.dp))
                Card(colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        c.phones.forEach { p -> Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(p.first,color=fg,fontSize=16.sp);Text(p.second+"  |  India",color=muted,fontSize=12.sp) }
                            IconButton(onClick={onCall(p.first)}) { Icon(Icons.Default.Call,null,tint=green) }
                            IconButton(onClick={onSms(p.first)}) { Icon(Icons.Default.Message,null,tint=green) }
                        } }
                        c.emails.forEach { Text(it.first,color=fg,modifier=Modifier.padding(vertical=8.dp));Text(it.second,color=muted,fontSize=12.sp) }
                        c.addresses.forEach { Text(it.first,color=fg,modifier=Modifier.padding(vertical=8.dp));Text(it.second,color=muted,fontSize=12.sp) }
                        c.birthday?.let { Text(it,color=fg,modifier=Modifier.padding(vertical=8.dp));Text("Birthday",color=muted,fontSize=12.sp) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Card(colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal=15.dp)) {
                        ContactAction("Video call",Icons.Default.Videocam,blue,fg) { c.phones.firstOrNull()?.first?.let(onVideo) }
                        ContactAction("WhatsApp",Icons.Default.Chat,green,fg) { c.phones.firstOrNull()?.first?.let(onWhatsApp) }
                        ContactAction("Telegram",Icons.Default.Send,blue,fg) { c.phones.firstOrNull()?.first?.let(onTelegram) }
                    }
                }
                Text("Call history",color=muted,fontSize=13.sp,modifier=Modifier.padding(start=12.dp,top=18.dp,bottom=8.dp))
                Card(colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        (if(allHistory) history else history.take(3)).forEach { call ->
                            Column(Modifier.fillMaxWidth().clickable{onCallDetail(call)}.padding(vertical=9.dp)) {
                                Text(SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(call.date)),color=if(call.type==CallLog.Calls.MISSED_TYPE) red else fg,fontWeight=FontWeight.Medium)
                                Text(call.number,color=muted,fontSize=12.sp);Text(callSummary(call),color=muted,fontSize=12.sp)
                            }
                        }
                        if(history.size>3 && !allHistory) Text("Show more",color=blue,modifier=Modifier.clickable(onClick=onHistory).padding(vertical=8.dp))
                        if(history.isEmpty()) Text("No call history",color=muted)
                    }
                }
                Text("More",color=muted,fontSize=13.sp,modifier=Modifier.padding(start=12.dp,top=18.dp,bottom=8.dp))
                Card(colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal=15.dp)) {
                        ContactAction("Default ringtone",Icons.Default.MusicNote,muted,fg,onRingtone)
                        ContactAction("QR code",Icons.Default.QrCode,muted,fg,onQr)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryPage(call:DialCall,history:List<DialCall>,fg:Color,muted:Color,card:Color,green:Color,red:Color,onBack:()->Unit,onCall:()->Unit,onNote:()->Unit,onRecordings:()->Unit,onDelete:()->Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBack,null,tint=fg) }
            Text("Call history details",Modifier.weight(1f),fontSize=19.sp,color=fg,textAlign=TextAlign.Center)
            IconButton(onClick=onCall) { Icon(Icons.Default.Call,null,tint=green) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal=18.dp),contentPadding=PaddingValues(bottom=12.dp)) {
            items(history.sortedByDescending{it.date},key={it.id}) { c ->
                Column(Modifier.fillMaxWidth().background(card,RoundedCornerShape(12.dp)).clickable(onClick=onCall).padding(14.dp)) {
                    Text(SimpleDateFormat("MMM d  h:mm a",Locale.getDefault()).format(Date(c.date)),color=fg,fontWeight=FontWeight.Medium)
                    Text(c.number,color=muted,fontSize=12.sp);Text(callSummary(c),color=muted,fontSize=12.sp)
                }
                Spacer(Modifier.height(7.dp))
            }
        }
        NavigationBar(containerColor=Color.Transparent) {
            NavigationBarItem(selected=false,onClick=onRecordings,icon={Icon(Icons.Default.Voicemail,null)},label={Text("Recorded calls")})
            NavigationBarItem(selected=false,onClick=onNote,icon={Icon(Icons.Default.Notes,null)},label={Text("Call notes")})
            NavigationBarItem(selected=false,onClick=onDelete,icon={Icon(Icons.Default.Delete,null)},label={Text("Delete")})
        }
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
@Composable
private fun ContactRow(c:DialContact,dark:Boolean,fg:Color,muted:Color,line:Color,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if(dark) Color(0xFF343438) else Color(0xFFE7E7EC)),contentAlignment=Alignment.Center) {
            Text(c.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",color=muted,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        }
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
    val keyBg=if(fg.red<0.5f) Color(0xFFE5E5EA) else Color(0xFF2C2C2E)
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
                            fontSize=8.sp,color=if(fg.red<0.5f) Color(0xFF66666D) else Color(0xFFB7B7BC),
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
private fun playDialTone(key:String) {
    try {
        val tone=when(key){"*"->ToneGenerator.TONE_DTMF_S;"#"->ToneGenerator.TONE_DTMF_P;else->ToneGenerator.TONE_DTMF_0+(key.firstOrNull()?.digitToIntOrNull()?:0)}
        val tg=ToneGenerator(AudioManager.STREAM_DTMF,70);tg.startTone(tone,100);Handler(Looper.getMainLooper()).postDelayed({try{tg.release()}catch(_:Exception){}},140)
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
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.Contacts.STARRED),null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE NOCASE ASC")?.use { c ->
            val id=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);val name=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val num=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);val star=c.getColumnIndex(ContactsContract.Contacts.STARRED)
            while(c.moveToNext()) out.add(DialContact(c.getLong(id),c.getString(name).orEmpty(),c.getString(num).orEmpty(),c.getInt(star)==1))
        }
    } catch (_:Exception) {}
    return out.distinctBy{it.id}
}
private fun readDialContact(ctx:Context,id:Long):DialContactDetail {
    var name="Contact";val phones=mutableListOf<Pair<String,String>>();val emails=mutableListOf<Pair<String,String>>();val addresses=mutableListOf<Pair<String,String>>();var birthday:String?=null
    try {
        ctx.contentResolver.query(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id),arrayOf(ContactsContract.Contacts.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())name=it.getString(0).orEmpty()}
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
    return DialContactDetail(id,name,phones.distinctBy{it.first.filter(Char::isDigit)},emails.distinctBy{it.first.lowercase(Locale.getDefault())},addresses.distinctBy{it.first},birthday)
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
