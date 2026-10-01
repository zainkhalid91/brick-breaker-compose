/*
 * Copyright 2026 Zain Khalid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zainkhalid.brickbreaker.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Glossy pill button with a physical press. */
@Composable
fun BrickPillButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF0B1030),
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "pill-press",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                // Glow: same shape drawn bigger and fainter.
                for (k in 1..2) {
                    val gx = (4 * k).dp.toPx()
                    val gy = (3 * k).dp.toPx()
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(-gx, -gy),
                        size = Size(size.width + gx * 2, size.height + gy * 2),
                        cornerRadius = r,
                        alpha = if (k == 1) 0.3f else 0.15f,
                    )
                }
            }
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.45f), color, lerp(color, Color.Black, 0.25f))))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) currentOnClick()
                    },
                )
            }
            .padding(horizontal = 40.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
        )
    }
}

/** Extruded title word. */
@Composable
fun BrickTitleWord(
    text: String,
    brush: Brush,
    fontSize: TextUnit,
    depthColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        for (i in 6 downTo 1) {
            Text(
                text = text,
                style = TextStyle(color = depthColor, fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                modifier = Modifier.offset(y = (i * 1.2f).dp),
            )
        }
        Text(
            text = text,
            style = TextStyle(
                color = Color(0xFF140B26),
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                drawStyle = Stroke(width = 12f, join = StrokeJoin.Round),
            ),
        )
        Text(
            text = text,
            style = TextStyle(brush = brush, fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
        )
    }
}
