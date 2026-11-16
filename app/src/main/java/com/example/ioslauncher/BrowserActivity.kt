package com.example.ioslauncher

import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri

class BrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NativeBrowser() }
    }
}

@Composable
private fun NativeBrowser() {
    val context= LocalContext.current
    var address by remember { mutableStateOf("https://www.google.com") }
    var current by remember { mutableStateOf("https://www.google.com") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val db = remember { LauncherDb(context) }
    BackHandler(enabled=webView?.canGoBack()==true) { webView?.goBack() }
    Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues())) {
        Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(address,{address=it},Modifier.weight(1f),singleLine=true,shape=RoundedCornerShape(18.dp),placeholder={Text("Search or enter website")})
            Button(onClick={
                val raw=address.trim()
                val url=when {
                    raw.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE)) -> raw
                    raw.contains(".") && !raw.contains(" ") -> "https://$raw"
                    else -> "https://www.google.com/search?q="+Uri.encode(raw)
                }
                current=url;webView?.loadUrl(url)
            }) { Text("Go") }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            IconButton(onClick={webView?.goBack()}){Icon(Icons.Default.ArrowBack,null)}
            IconButton(onClick={webView?.goForward()}){Icon(Icons.Default.ArrowForward,null)}
            IconButton(onClick={webView?.reload()}){Icon(Icons.Default.Refresh,null)}
            IconButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(current)))}){Icon(Icons.Default.MoreVert,null)}
        }
        AndroidView(
            modifier=Modifier.fillMaxSize(),
            factory={ctx->
                WebView(ctx).apply {
                    settings.javaScriptEnabled=true
                    settings.domStorageEnabled=true
                    settings.loadsImagesAutomatically=true
                    settings.useWideViewPort=true
                    settings.loadWithOverviewMode=true
                    webViewClient=object:WebViewClient(){
                        override fun onPageFinished(view:WebView?, url:String?){
                            super.onPageFinished(view,url)
                            url?.let {
                                current=it
                                address=it
                                db.put("browser.last_url", it)
                                db.log("browser_navigation", it)
                            }
                        }
                    }
                    webChromeClient=WebChromeClient()
                    loadUrl(current)
                    webView=this
                }
            },
            update={ webView=it }
        )
    }
}
