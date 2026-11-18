package com.example.ioslauncher

@Composable
fun HomeEntryMotion(
    revision:Int,
    order:Int,
    modifier:Modifier=Modifier,
    content:@Composable BoxScope.()->Unit
) {
    var entered by remember { mutableStateOf(revision <= 0) }
    LaunchedEffect(revision) {
        if (revision <= 0) {
            entered=true
        } else {
            entered=false
            delay((order.coerceAtLeast(0)*28L).coerceAtMost(420L))
            entered=true
        }
    }
    val scale by animateFloatAsState(
        targetValue=if(entered) 1f else .76f,
        animationSpec=spring(dampingRatio=.68f,stiffness=480f),
        label="home-entry-scale"
    )
    val alpha by animateFloatAsState(
        targetValue=if(entered) 1f else 0f,
        animationSpec=tween(durationMillis=220),
        label="home-entry-alpha"
    )
    val offsetY by animateDpAsState(
        targetValue=if(entered) 0.dp else 22.dp,
        animationSpec=spring(dampingRatio=.72f,stiffness=430f),
        label="home-entry-offset"
    )
    Box(
        modifier=modifier.graphicsLayer {
            scaleX=scale
            scaleY=scale
            this.alpha=alpha
            translationY=offsetY.toPx()
        },
        contentAlignment=Alignment.TopCenter,
        content=content
    )
}

private fun greeting():String = when(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun HomeWidget(kicker:String, value:String, subtitle:String, icon:ImageVector, modifier:Modifier, accent:Color) {
    Surface(modifier.height(136.dp), shape=RoundedCornerShape(25.dp), color=Color.White.copy(alpha=.84f)) {
        Column(Modifier.padding(15.dp), verticalArrangement=Arrangement.SpaceBetween) {
            Text(kicker, color=Color(0xFF777781), fontSize=10.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(value, color=accent, fontSize=33.sp, fontWeight=FontWeight.Light, modifier=Modifier.weight(1f))
                Icon(icon, null, tint=accent, modifier=Modifier.size(28.dp))
            }
            Text(subtitle, color=Color(0xFF55555E), fontSize=12.sp, maxLines=1)
        }
    }
}

fun iconResourceForLabel(label:String, packageName:String = ""):Int {
    val n=label.lowercase(Locale.getDefault())
    return when {
        "calendar" in n -> R.drawable.ios18_calendar
        "camera" in n -> R.drawable.ios18_camera
        "message" in n || "sms" in n -> R.drawable.ios18_messages
        "note" in n || "keep" in n -> R.drawable.ios18_notes
        "safari" in n || "browser" in n -> R.drawable.ios18_safari
        "chrome" in n -> R.drawable.ios18_chrome
        "setting" in n -> R.drawable.ios18_settings
        "weather" in n -> R.drawable.ios18_weather
        "photo" in n || "gallery" in n -> R.drawable.ios18_photos
        "file" in n -> R.drawable.ios18_files
        "music" in n || "spotify" in n -> R.drawable.ios18_music
        "phonepe" in n || "phonepe" in packageName.lowercase(Locale.getDefault()) -> 0
        packageName == "com.google.android.dialer" || packageName == "com.android.dialer" || n == "phone" || n == "dialer" -> R.drawable.ios18_phone
        "health" in n -> R.drawable.ios18_health
        "map" in n -> R.drawable.ios18_maps
        "amazon" in n -> R.drawable.ios18_amazon
        "gmail" in n || "mail" in n -> R.drawable.ios18_gmail
        "calculator" in n -> R.drawable.ios18_calculator
        "youtube" in n -> R.drawable.ios18_youtube
        "whatsapp" in n -> R.drawable.ios18_whatsapp
        "app store" in n || "play store" in n -> R.drawable.ios18_appstore
        else -> 0
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app:InstalledApp, labels:Boolean, fg:Color, index:Int=0, animationSpeed:String="Fast", size:Dp=62.dp,
    dragTranslation:Offset=Offset.Zero, isDragging:Boolean=false, draggableEnabled:Boolean=true,
    animateOnEntry:Boolean=true,
    onBounds:(String,Rect)->Unit={ _, _ -> }, onDragStart:()->Unit={}, onDrag:(Offset)->Unit={ _ -> },
    onDragEnd:()->Unit={}, onLongClick:(()->Unit)?=null, onClick:()->Unit
) {
    var shown by remember(app.packageName,animateOnEntry) { mutableStateOf(!animateOnEntry) }
    LaunchedEffect(app.packageName,animateOnEntry) {
        if (animateOnEntry) shown=true else shown=true
    }
    val duration = when(animationSpeed) { "Slow" -> 300; "Normal" -> 210; else -> 140 }
    val scale by animateFloatAsState(if(shown) 1f else .96f, spring(stiffness=androidx.compose.animation.core.Spring.StiffnessMediumLow), label="icon-scale")
    val alpha by animateFloatAsState(if(shown) 1f else 0f, androidx.compose.animation.core.tween(duration), label="icon-alpha")
    val iconRes=iconResourceForLabel(app.label, app.packageName)
    val fallbackBitmap = remember(app.packageName) { app.icon.toBitmap(192,192).asImageBitmap() }
    Column(
        horizontalAlignment=Alignment.CenterHorizontally,
        modifier=Modifier.graphicsLayer {
            translationX=dragTranslation.x
            translationY=dragTranslation.y
            scaleX=scale * if(isDragging) 1.10f else 1f
            scaleY=scale * if(isDragging) 1.10f else 1f
            this.alpha=if(isDragging) .88f else alpha
        }
            .onGloballyPositioned { onBounds(app.packageName,it.boundsInRoot()) }
            .then(if(draggableEnabled) Modifier.pointerInput(app.packageName) {
                detectDragGesturesAfterLongPress(
                    onDragStart={ onDragStart() },
                    onDrag={ change, amount -> change.consume(); onDrag(amount) },
                    onDragEnd=onDragEnd,
                    onDragCancel=onDragEnd
                )
            } else Modifier)
            .combinedClickable(
                interactionSource=remember { MutableInteractionSource() },
                indication=null,
                onClick=onClick,
                onLongClick=onLongClick
            )
    ) {
        Surface(modifier=Modifier.size(size).shadow(5.dp,RoundedCornerShape(21.dp)),
            shape=RoundedCornerShape(21.dp), color=Color.White) {
            if(iconRes!=0) {
                Image(painterResource(iconRes),null,Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)))
            } else {
                Image(fallbackBitmap,null,Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)).padding(1.dp))
            }
        }
        if(labels) {
            Spacer(Modifier.height(6.dp))
            Text(app.label,color=fg,fontSize=11.sp,maxLines=1,textAlign=TextAlign.Center)
        }
    }
}

@Composable
fun FolderTile(folder:HomeFolder, apps:List<InstalledApp>, fg:Color, onClick:()->Unit) {
    val members=folder.packages.mapNotNull { pkg -> apps.firstOrNull { it.packageName==pkg } }.take(4)
    Column(Modifier.fillMaxWidth().clickable(onClick=onClick),horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(shape=RoundedCornerShape(17.dp),color=Color.White.copy(alpha=.78f),modifier=Modifier.size(62.dp)) {
            Column(Modifier.padding(7.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        repeat(2) { col ->
                            val member=members.getOrNull(row*2+col)
                            if(member!=null) {
                                val icon=remember(member.packageName) { member.icon.toBitmap(72,72).asImageBitmap() }
                                Image(icon,contentDescription=member.label,modifier=Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)))
                            } else Spacer(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Color.LightGray.copy(alpha=.35f)))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(folder.title,color=fg,fontSize=10.sp,maxLines=1,textAlign=TextAlign.Center)
    }
}

@Composable
fun DockIosIcon(resId:Int, label:String, fg:Color, onClick:()->Unit) {
    Column(Modifier.clickable(
        interactionSource=remember { MutableInteractionSource() }, indication=null, onClick=onClick
    ).padding(horizontal=7.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Image(painterResource(resId), contentDescription=label, modifier=Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)))
        Text(label, color=fg, fontSize=9.sp)
    }
}

@Composable
fun DockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, fg: Color, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(horizontal=8.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(icon, null, tint=fg, modifier=Modifier.size(28.dp))
        Text(label, color=fg, fontSize=9.sp)
    }
}
