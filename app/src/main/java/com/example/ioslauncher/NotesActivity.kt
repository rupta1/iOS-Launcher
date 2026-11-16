package com.example.ioslauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class NotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db=LauncherDb(this)
        setContent {
            var text by remember { mutableStateOf(db.get("notes.main")) }
            Column(Modifier.fillMaxSize().padding(18.dp).padding(WindowInsets.statusBars.asPaddingValues())) {
                Text("Notes",style=MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text,{text=it;db.put("notes.main",it)},Modifier.fillMaxSize(),placeholder={Text("Write something…")},shape=RoundedCornerShape(18.dp))
            }
        }
    }
}
