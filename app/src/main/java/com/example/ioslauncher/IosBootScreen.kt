package com.example.ioslauncher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun IosBootScreen() {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 2450, easing = FastOutSlowInEasing),
        label = "bootProgress"
    )
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.width(88.dp).height(104.dp)) {
                val sx = size.width / 100f
                val sy = size.height / 118f
                val apple = Path().apply {
                    moveTo(50f*sx, 29f*sy)
                    cubicTo(40f*sx, 18f*sy, 23f*sx, 21f*sy, 14f*sx, 34f*sy)
                    cubicTo(2f*sx, 51f*sy, 12f*sx, 82f*sy, 25f*sx, 99f*sy)
                    cubicTo(33f*sx, 110f*sy, 40f*sx, 105f*sy, 50f*sx, 105f*sy)
                    cubicTo(61f*sx, 105f*sy, 67f*sx, 111f*sy, 77f*sx, 99f*sy)
                    cubicTo(88f*sx, 84f*sy, 99f*sx, 53f*sy, 87f*sx, 36f*sy)
                    cubicTo(77f*sx, 22f*sy, 60f*sx, 20f*sy, 50f*sx, 29f*sy)
                    close()
                }
                drawPath(apple, Color.White, style = Fill)
                val bite = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(73f*sx, 44f*sy, 96f*sx, 68f*sy))
                }
                drawPath(bite, Color.Black, style = Fill)
                val leaf = Path().apply {
                    moveTo(51f*sx, 23f*sy)
                    cubicTo(52f*sx, 9f*sy, 65f*sx, 1f*sy, 79f*sx, 2f*sy)
                    cubicTo(78f*sx, 16f*sy, 67f*sx, 25f*sy, 51f*sx, 23f*sy)
                    close()
                }
                drawPath(leaf, Color.White, style = Fill)
            }
            Spacer(Modifier.height(38.dp))
            Box(
                modifier = Modifier.width(126.dp).height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF454545))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                )
            }
        }
    }
}
