package com.example.ioslauncher

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

@Composable
fun AppleBootScreen() {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 2600, easing = LinearEasing),
        label = "appleBootProgress"
    )

    // The supplied 24x24 SVG path, rendered in white on the black boot screen.
    val appleLogo = PathParser().parsePathString(
        "M12 5.5c0 -3 1.5 -4.5 4.5 -4.5 0 3 -1.5 4.5 -4.5 4.5Zm-1.327 1.633c-.642 -.295 -1.378 -.633 -2.337 -.633 -2.129 0 -5.323 2.129 -5.323 6.387C3.013 17.145 5.713 23 8.336 23c.798 0 1.33 -.266 1.863 -.532 .532 -.266 1.064 -.532 1.863 -.532 .798 0 1.463 .266 2.129 .532 .665 .266 1.33 .532 2.129 .532 1.835 0 3.707 -2.864 4.666 -6.044 -.882 -.32 -2.646 -1.971 -2.646 -4.069 0 -2.706 1.237 -3.923 1.855 -4.33C19.08 7.186 17.528 6.5 16.32 6.5c-.96 0 -1.864 .338 -2.654 .633 -.613 .228 -1.157 .432 -1.604 .432 -.448 0 -.89 -.204 -1.389 -.432Z"
    ).toPath().apply { fillType = PathFillType.EvenOdd }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.width(92.dp).height(92.dp)) {
                val scale = minOf(size.width, size.height) / 24f
                val left = (size.width - 24f * scale) / 2f
                val top = (size.height - 24f * scale) / 2f
                withTransform({
                    translate(left, top)
                    scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
                }) {
                    drawPath(appleLogo, Color.White)
                }
            }
            Spacer(Modifier.height(38.dp))
            Box(
                modifier = Modifier.width(126.dp).height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF454545))
            ) {
                Box(
                    modifier = Modifier.width(126.dp * progress).height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                )
            }
        }
    }
}
