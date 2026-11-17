package com.example.ioslauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.CallLog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import java.util.Locale

private data class PhoneContact(val name:String,val number:String,val starred:Boolean)

class PhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(246,246,250)
        window.navigationBarColor=android.graphics.Color.rgb(246,246,250)
        setContent { MaterialTheme { IosPhoneScreen() } }
    }
}

@Composable
private fun IosPhoneScreen() {
    val context= LocalContext.current
    var tab by remember { mutableStateOf("Favorites") }
    var number by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<PhoneContact>()) }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED) }
    val permission= rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission=granted
        if(granted) contacts=loadPhoneContacts(context)
    }
    LaunchedEffect(hasPermission) { if(hasPermission) contacts=loadPhoneContacts(context) }
    BackHandler(enabled=number.isNotEmpty()) { number=number.dropLast(1) }
    val visibleContacts=contacts.filter { (tab!="Favorites" || it.starred) && (it.name.contains(query,true) || it.number.contains(query,true)) }
    fun dial(value:String) {
        if(value.isNotBlank()) try {
            context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(value))))
        } catch (_:Exception) { }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5FA)).padding(WindowInsets.statusBars.asPaddingValues())) {
        Row(Modifier.fillMaxWidth().padding(horizontal=22.dp, vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Phone",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Color(0xFF15151A))
                Text("Calls and contacts",fontSize=13.sp,color=Color(0xFF777780))
            }
            if(tab=="Contacts") IconButton(onClick={
                try { context.startActivity(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)) } catch (_:Exception) { }
            }) { Icon(Icons.Default.PersonAdd,null,tint=Color(0xFF30B968)) }
            IconButton(onClick={
                try { context.startActivity(Intent("android.settings.APPLICATION_DETAILS_SETTINGS",Uri.parse("package:"+context.packageName))) } catch (_:Exception) { }
            }) { Icon(Icons.Default.Settings,null,tint=Color(0xFF6D6D75)) }
        }
        AnimatedContent(targetState=tab,transitionSpec={fadeIn() togetherWith fadeOut()},modifier=Modifier.weight(1f),label="phone-tabs") { current ->
            when(current) {
                "Keypad" -> Column(Modifier.fillMaxSize().padding(horizontal=30.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    Text(number.ifBlank { " " },fontSize=30.sp,color=Color(0xFF17171C),textAlign=TextAlign.Center,maxLines=1)
                    Spacer(Modifier.height(24.dp))
                    val keys=listOf("1","2","3","4","5","6","7","8","9","*","0","#")
                    keys.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                            row.forEach { key ->
                                Surface(onClick={number+=key},shape=CircleShape,color=Color.White,modifier=Modifier.padding(7.dp).size(72.dp)) {
                                    Box(contentAlignment=Alignment.Center) {
                                        Text(key,fontSize=27.sp,color=Color(0xFF22222A),fontWeight=FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        Spacer(Modifier.width(54.dp))
                        Surface(onClick={dial(number)},shape=CircleShape,color=Color(0xFF30C66B),modifier=Modifier.size(64.dp)) {
                            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Call,null,tint=Color.White,modifier=Modifier.size(28.dp)) }
                        }
                        Spacer(Modifier.width(18.dp))
                        IconButton(onClick={number=number.dropLast(1)}) { Icon(Icons.Default.Backspace,null,tint=Color(0xFF60606A)) }
                    }
                    Text("Calls are handed to Android's system dialer for confirmation.",fontSize=11.sp,color=Color(0xFF85858E),textAlign=TextAlign.Center,modifier=Modifier.padding(top=12.dp))
                }
                "Recents" -> Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Icon(Icons.Default.History,null,tint=Color(0xFF8A8A93),modifier=Modifier.size(54.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Recent calls",fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF22222A))
                    Text("Your device keeps call history in the system Phone app.",fontSize=13.sp,color=Color(0xFF777780),textAlign=TextAlign.Center,modifier=Modifier.padding(12.dp))
                    Button(onClick={try { context.startActivity(Intent(Intent.ACTION_VIEW,CallLog.Calls.CONTENT_URI)) } catch (_:Exception) { dial("") }}) { Text("Open call history") }
                }
                else -> Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
                    OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                        placeholder={Text("Search contacts")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(18.dp))
                    if(!hasPermission) {
                        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Icon(Icons.Default.Contacts,null,tint=Color(0xFF85858E),modifier=Modifier.size(52.dp))
                            Text("Allow contacts access to see your contacts",color=Color(0xFF55555E),textAlign=TextAlign.Center,modifier=Modifier.padding(18.dp))
                            Button(onClick={permission.launch(Manifest.permission.READ_CONTACTS)}) { Text("Allow contacts") }
                        }
                    } else if(visibleContacts.isEmpty()) {
                        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("No contacts found",color=Color(0xFF85858E)) }
                    } else LazyColumn(contentPadding=PaddingValues(vertical=12.dp)) {
                        items(visibleContacts,key={it.name+it.number}) { contact ->
                            Row(Modifier.fillMaxWidth().clickable { dial(contact.number) }.padding(vertical=10.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE5E5ED)),contentAlignment=Alignment.Center) {
                                    Text(contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",fontSize=18.sp,color=Color(0xFF55556A),fontWeight=FontWeight.SemiBold)
                                }
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(contact.name,color=Color(0xFF22222A),fontSize=15.sp,fontWeight=FontWeight.Medium)
                                    Text(contact.number,color=Color(0xFF85858E),fontSize=12.sp)
                                }
                                if(contact.starred) Icon(Icons.Default.Star,null,tint=Color(0xFFFFB52E),modifier=Modifier.size(18.dp))
                                IconButton(onClick={dial(contact.number)}) { Icon(Icons.Default.Call,null,tint=Color(0xFF30B968)) }
                            }
                            HorizontalDivider(color=Color(0xFFE7E7ED))
                        }
                    }
                }
            }
        }
        NavigationBar(containerColor=Color.White) {
            listOf("Favorites" to Icons.Default.Star,"Recents" to Icons.Default.History,"Contacts" to Icons.Default.Contacts,"Keypad" to Icons.Default.Dialpad).forEach { (label,icon) ->
                NavigationBarItem(selected=tab==label,onClick={tab=label},icon={Icon(icon,null)},label={Text(label,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Color(0xFF30B968),selectedTextColor=Color(0xFF30B968),indicatorColor=Color(0xFFE8F8EE)))
            }
        }
    }
}

private fun loadPhoneContacts(context:android.content.Context):List<PhoneContact> {
    if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) return emptyList()
    val result=mutableListOf<PhoneContact>()
    try {
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.STARRED),
            null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE NOCASE ASC")?.use { cursor ->
            val nameIndex=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val starIndex=cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
            while(cursor.moveToNext()) {
                val name=cursor.getString(nameIndex).orEmpty()
                val number=cursor.getString(numberIndex).orEmpty()
                if(name.isNotBlank() && number.isNotBlank()) result.add(PhoneContact(name,number,cursor.getInt(starIndex)==1))
            }
        }
    } catch (_:SecurityException) { }
    return result.distinctBy { it.number }
}

@Composable
fun IosBootScreen() {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF08090D),Color(0xFF151923)))),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Box(Modifier.size(92.dp),contentAlignment=Alignment.Center) {
            Icon(Icons.Default.PhoneIphone,null,tint=Color.White,modifier=Modifier.size(54.dp))
            CircularProgressIndicator(progress={0.72f},modifier=Modifier.fillMaxSize(),color=Color.White.copy(alpha=.82f),strokeWidth=2.dp,trackColor=Color.White.copy(alpha=.12f))
        }
        Spacer(Modifier.height(24.dp))
        Text("iOS",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Light,letterSpacing=5.sp)
        Spacer(Modifier.height(8.dp))
        Text("Starting",color=Color.White.copy(alpha=.52f),fontSize=12.sp,letterSpacing=1.4.sp)
    }
}
