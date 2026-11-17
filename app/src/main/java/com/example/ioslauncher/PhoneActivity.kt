package com.example.ioslauncher

import android.Manifest
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PhoneContact(val name:String,val number:String,val starred:Boolean)
private data class RecentCall(val name:String,val number:String,val type:Int,val date:Long)

class PhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark=isSystemInDarkTheme()
            MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
                IosPhoneScreen()
            }
        }
    }
}

@Composable
private fun IosPhoneScreen() {
    val context=LocalContext.current
    val dark=isSystemInDarkTheme()
    val background=if(dark) Color(0xFF101114) else Color(0xFFF5F5FA)
    val surface=if(dark) Color(0xFF1C1D22) else Color.White
    val primaryText=if(dark) Color(0xFFF4F4F7) else Color(0xFF17171C)
    val secondaryText=if(dark) Color(0xFFA5A6AE) else Color(0xFF85858E)
    val divider=if(dark) Color(0xFF303139) else Color(0xFFE7E7ED)
    val green=Color(0xFF30C66B)
    val prefs=remember { context.getSharedPreferences("phone_preferences",android.content.Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf("Recents") }
    var number by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<PhoneContact>()) }
    var recents by remember { mutableStateOf(emptyList<RecentCall>()) }
    var hasCallLogPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED) }
    var hasCallPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED) }
    var pendingCall by remember { mutableStateOf("") }
    var keypadSound by remember { mutableStateOf(prefs.getBoolean("keypad_sound",true)) }
    var menuExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }

    val callPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasCallPermission=result[Manifest.permission.CALL_PHONE]==true || ContextCompat.checkSelfPermission(context,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED
        hasCallLogPermission=result[Manifest.permission.READ_CALL_LOG]==true || ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED
        if(hasCallLogPermission) recents=loadRecentCalls(context)
        if(hasCallPermission && pendingCall.isNotBlank()) {
            val value=pendingCall
            pendingCall=""
            try { context.startActivity(Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(value)))) }
            catch (_:Exception) { context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(value)))) }
        }
    }
    val callLogPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCallLogPermission=granted
        if(granted) recents=loadRecentCalls(context)
    }
    var hasContactsPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED) }
    val contactsPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasContactsPermission=granted
        if(granted) contacts=loadPhoneContacts(context)
    }
    LaunchedEffect(hasContactsPermission) { if(hasContactsPermission) contacts=loadPhoneContacts(context) }
    LaunchedEffect(tab,hasCallLogPermission) { if(tab=="Recents" && hasCallLogPermission) recents=loadRecentCalls(context) }
    BackHandler(enabled=number.isNotEmpty() || menuExpanded || appearanceExpanded) {
        when {
            appearanceExpanded -> appearanceExpanded=false
            menuExpanded -> menuExpanded=false
            else -> number=number.dropLast(1)
        }
    }
    val visibleContacts=contacts.filter { (tab!="Favorites" || it.starred) && (it.name.contains(query,true) || it.number.contains(query,true)) }

    fun playKeyTone(key:String) {
        if(!keypadSound) return
        try {
            val tone=when(key) {
                "*" -> ToneGenerator.TONE_DTMF_S
                "#" -> ToneGenerator.TONE_DTMF_P
                else -> ToneGenerator.TONE_DTMF_0+(key.firstOrNull()?.digitToIntOrNull() ?: 0)
            }
            val generator=ToneGenerator(AudioManager.STREAM_DTMF,75)
            generator.startTone(tone,110)
            Handler(Looper.getMainLooper()).postDelayed({ try { generator.release() } catch (_:Exception) {} },160)
        } catch (_:Exception) { }
    }
    fun dial(value:String) {
        if(value.isBlank()) return
        if(!hasCallPermission) {
            pendingCall=value
            callPermission.launch(arrayOf(Manifest.permission.CALL_PHONE,Manifest.permission.READ_CALL_LOG))
            return
        }
        try { context.startActivity(Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(value)))) }
        catch (_:SecurityException) { context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(value)))) }
        catch (_:Exception) { }
    }

    Column(Modifier.fillMaxSize().background(background).padding(WindowInsets.statusBars.asPaddingValues())) {
        Row(Modifier.fillMaxWidth().padding(start=22.dp,end=12.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(tab=="Recents") "Recents" else if(tab=="Keypad") "Keypad" else if(tab=="Contacts") "Contacts" else "Favorites",
                    fontSize=32.sp,fontWeight=FontWeight.Bold,color=primaryText)
                if(tab=="Recents") Text("Recent calls",fontSize=13.sp,color=secondaryText)
            }
            if(tab=="Contacts") {
                IconButton(onClick={
                    try { context.startActivity(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)) } catch (_:Exception) {}
                }) { Icon(Icons.Default.PersonAdd,null,tint=green) }
            }
            Box {
                IconButton(onClick={menuExpanded=true}) { Icon(Icons.Default.MoreVert,null,tint=primaryText) }
                DropdownMenu(expanded=menuExpanded,onDismissRequest={menuExpanded=false},modifier=Modifier.background(surface)) {
                    DropdownMenuItem(
                        text={ Text("Keypad sound") },
                        trailingIcon={ Switch(checked=keypadSound,onCheckedChange={
                            keypadSound=it
                            prefs.edit().putBoolean("keypad_sound",it).apply()
                        }) },
                        onClick={
                            keypadSound=!keypadSound
                            prefs.edit().putBoolean("keypad_sound",keypadSound).apply()
                        }
                    )
                    DropdownMenuItem(
                        text={ Text("Appearance") },
                        trailingIcon={ Icon(Icons.Default.ChevronRight,null) },
                        onClick={ appearanceExpanded=true; menuExpanded=false }
                    )
                    DropdownMenuItem(
                        text={ Text("Call settings") },
                        onClick={
                            menuExpanded=false
                            try { context.startActivity(Intent("android.telephony.action.CALL_SETTINGS")) }
                            catch (_:Exception) { try { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_:Exception) {} }
                        }
                    )
                    DropdownMenuItem(
                        text={ Text("Contacts settings") },
                        onClick={
                            menuExpanded=false
                            try { context.startActivity(Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)) } catch (_:Exception) {}
                        }
                    )
                }
                DropdownMenu(expanded=appearanceExpanded,onDismissRequest={appearanceExpanded=false},modifier=Modifier.background(surface)) {
                    DropdownMenuItem(
                        text={ Text("System default"+if(dark) " · Dark" else " · Light") },
                        leadingIcon={ Icon(Icons.Default.Check,null,tint=green) },
                        onClick={appearanceExpanded=false}
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(targetState=tab,transitionSpec={fadeIn() togetherWith fadeOut()},modifier=Modifier.fillMaxSize(),label="phone-tabs") { current ->
                when(current) {
                    "Keypad" -> Column(Modifier.fillMaxSize().padding(horizontal=30.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(18.dp))
                        Text(number.ifBlank { " " },fontSize=30.sp,color=primaryText,textAlign=TextAlign.Center,maxLines=1)
                        Spacer(Modifier.height(20.dp))
                        val keys=listOf("1","2","3","4","5","6","7","8","9","*","0","#")
                        keys.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                row.forEach { key ->
                                    Surface(onClick={number+=key;playKeyTone(key)},shape=CircleShape,color=surface,modifier=Modifier.padding(7.dp).size(72.dp)) {
                                        Box(contentAlignment=Alignment.Center) { Text(key,fontSize=27.sp,color=primaryText) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                            Spacer(Modifier.width(54.dp))
                            Surface(onClick={dial(number)},shape=CircleShape,color=green,modifier=Modifier.size(64.dp)) {
                                Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Call,null,tint=Color.White,modifier=Modifier.size(28.dp)) }
                            }
                            Spacer(Modifier.width(18.dp))
                            IconButton(onClick={number=number.dropLast(1)}) { Icon(Icons.Default.Backspace,null,tint=secondaryText) }
                        }
                    }
                    "Recents" -> {
                        if(!hasCallLogPermission) {
                            Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                Icon(Icons.Default.History,null,tint=secondaryText,modifier=Modifier.size(54.dp))
                                Text("Allow call history access to view recent calls",fontSize=14.sp,color=secondaryText,textAlign=TextAlign.Center,modifier=Modifier.padding(18.dp))
                                Button(onClick={callLogPermission.launch(Manifest.permission.READ_CALL_LOG)}) { Text("Allow call history") }
                            }
                        } else if(recents.isEmpty()) {
                            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("No recent calls",color=secondaryText) }
                        } else LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),contentPadding=PaddingValues(top=8.dp,bottom=92.dp)) {
                            items(recents) { call ->
                                Row(Modifier.fillMaxWidth().clickable { number=call.number; tab="Keypad" }.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Box(Modifier.size(44.dp).clip(CircleShape).background(if(dark) Color(0xFF303139) else Color(0xFFE5E5ED)),contentAlignment=Alignment.Center) {
                                        Icon(if(call.type==CallLog.Calls.OUTGOING_TYPE) Icons.Default.Call else Icons.Default.CallReceived,null,tint=if(call.type==CallLog.Calls.MISSED_TYPE) Color(0xFFE14D5A) else green)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(call.name.ifBlank { call.number.ifBlank { "Unknown" } },color=primaryText,fontSize=15.sp,fontWeight=FontWeight.Medium)
                                        Text(call.number+"  ·  "+when(call.type) { CallLog.Calls.INCOMING_TYPE -> "Incoming"; CallLog.Calls.OUTGOING_TYPE -> "Outgoing"; CallLog.Calls.MISSED_TYPE -> "Missed"; else -> "Call" },color=secondaryText,fontSize=12.sp)
                                    }
                                    Text(SimpleDateFormat("dd MMM, h:mm a",Locale.getDefault()).format(Date(call.date)),color=secondaryText,fontSize=11.sp)
                                    IconButton(onClick={dial(call.number)}) { Icon(Icons.Default.Call,null,tint=green) }
                                }
                                HorizontalDivider(color=divider)
                            }
                        }
                    }
                    else -> Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
                        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                            placeholder={Text("Search contacts")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(18.dp))
                        if(!hasContactsPermission) {
                            Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                Icon(Icons.Default.Contacts,null,tint=secondaryText,modifier=Modifier.size(52.dp))
                                Text("Allow contacts access to see your contacts",color=secondaryText,textAlign=TextAlign.Center,modifier=Modifier.padding(18.dp))
                                Button(onClick={contactsPermission.launch(Manifest.permission.READ_CONTACTS)}) { Text("Allow contacts") }
                            }
                        } else if(visibleContacts.isEmpty()) {
                            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("No contacts found",color=secondaryText) }
                        } else LazyColumn(contentPadding=PaddingValues(vertical=12.dp)) {
                            items(visibleContacts,key={it.name+it.number}) { contact ->
                                Row(Modifier.fillMaxWidth().clickable { dial(contact.number) }.padding(vertical=10.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Box(Modifier.size(46.dp).clip(CircleShape).background(if(dark) Color(0xFF303139) else Color(0xFFE5E5ED)),contentAlignment=Alignment.Center) {
                                        Text(contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",fontSize=18.sp,color=secondaryText,fontWeight=FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.width(13.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(contact.name,color=primaryText,fontSize=15.sp,fontWeight=FontWeight.Medium)
                                        Text(contact.number,color=secondaryText,fontSize=12.sp)
                                    }
                                    if(contact.starred) Icon(Icons.Default.Star,null,tint=Color(0xFFFFB52E),modifier=Modifier.size(18.dp))
                                    IconButton(onClick={dial(contact.number)}) { Icon(Icons.Default.Call,null,tint=green) }
                                }
                                HorizontalDivider(color=divider)
                            }
                        }
                    }
                }
            }
            if(tab=="Recents") {
                Surface(
                    onClick={tab="Keypad"},
                    shape=CircleShape,
                    color=green,
                    shadowElevation=8.dp,
                    modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp).size(62.dp)
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Icon(Icons.Default.Dialpad,null,tint=Color.White,modifier=Modifier.size(27.dp))
                    }
                }
            }
        }

        NavigationBar(containerColor=surface) {
            listOf("Favorites" to Icons.Default.Star,"Recents" to Icons.Default.History,"Contacts" to Icons.Default.Contacts,"Keypad" to Icons.Default.Dialpad).forEach { (label,icon) ->
                NavigationBarItem(selected=tab==label,onClick={tab=label},icon={Icon(icon,null)},label={Text(label,fontSize=10.sp)},
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=green,selectedTextColor=green,indicatorColor=if(dark) Color(0xFF183C29) else Color(0xFFE8F8EE),unselectedIconColor=secondaryText,unselectedTextColor=secondaryText))
            }
        }
    }
}

private fun loadRecentCalls(context:android.content.Context):List<RecentCall> {
    if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED) return emptyList()
    val result=mutableListOf<RecentCall>()
    try {
        context.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME,CallLog.Calls.NUMBER,CallLog.Calls.TYPE,CallLog.Calls.DATE),
            null,null,CallLog.Calls.DATE+" DESC")?.use { cursor ->
            val nameIndex=cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberIndex=cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex=cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex=cursor.getColumnIndex(CallLog.Calls.DATE)
            while(cursor.moveToNext()) result.add(RecentCall(cursor.getString(nameIndex).orEmpty(),cursor.getString(numberIndex).orEmpty(),cursor.getInt(typeIndex),cursor.getLong(dateIndex)))
        }
    } catch (_:SecurityException) { }
    return result
}

private fun loadPhoneContacts(context:android.content.Context):List<PhoneContact> {
    if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) return emptyList()
    val result=mutableListOf<PhoneContact>()
    try {
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.Contacts.STARRED),
            null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE NOCASE ASC")?.use { cursor ->
            val nameIndex=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val starIndex=cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
            while(cursor.moveToNext()) {
                val name=cursor.getString(nameIndex).orEmpty()
                val number=cursor.getString(numberIndex).orEmpty()
                if(name.isNotBlank() && number.isNotBlank()) result.add(PhoneContact(name,number,cursor.getInt(starIndex)==1))
            }
        }
    } catch (_:SecurityException) { }
    return result.distinctBy { it.number }
}
