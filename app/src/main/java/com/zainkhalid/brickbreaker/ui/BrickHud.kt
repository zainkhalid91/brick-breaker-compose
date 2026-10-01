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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zainkhalid.brickbreaker.engine.BrickEngine
import com.zainkhalid.brickbreaker.engine.BrickLevels
import com.zainkhalid.brickbreaker.engine.BrickTheme
import com.zainkhalid.brickbreaker.engine.START_LIVES
import com.zainkhalid.brickbreaker.render.BrickPalette
import kotlin.math.max

/** Top bar: level, score, combo, lives and pause. */
@Composable
fun BrickHud(engine: BrickEngine, theme: BrickTheme, onPause: () -> Unit) {
    val accent = BrickPalette.accent(theme)
    val bar by animateColorAsState(BrickPalette.hud(theme), tween(800), label = "hud-bar")
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF030306), bar)))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(104.dp)) {
                val bossLevel = BrickLevels[engine.levelIndex].boss != null
                Text(
                    text = (if (bossLevel) "BOSS " else "LEVEL ") + "${engine.levelIndex + 1}/${BrickLevels.size}",
                    color = if (bossLevel) Color(0xFFFF4E5E) else accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = BrickLevels[engine.levelIndex].name.uppercase(),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HudScore(
                score = { engine.score },
                combo = { engine.combo },
                theme = theme,
                modifier = Modifier.weight(1f),
            )
            HudLives(lives = { engine.lives })
            Spacer(Modifier.width(10.dp))
            PauseButton(accent = accent, onClick = onPause)
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent))),
        )
    }
}

/** Score digits plus a combo badge once a chain reaches ×3. */
@Composable
private fun HudScore(score: () -> Int, combo: () -> Int, theme: BrickTheme, modifier: Modifier = Modifier) {
    val accent = BrickPalette.accent(theme)
    val digits = listOf(lerp(accent, Color.White, 0.75f), accent, lerp(accent, Color.Black, 0.45f))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = score().toString().padStart(6, '0'),
            style = TextStyle(
                brush = Brush.verticalGradient(digits),
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            ),
        )
        val c = combo()
        Text(
            text = if (c >= 3) "COMBO ×${minOf(c, 8)}" else " ",
            color = BrickPalette.accent2(theme),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    }
}

/** One heart per life. */
@Composable
private fun HudLives(lives: () -> Int) {
    val n = lives()
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until max(n, START_LIVES)) {
            val tint by animateColorAsState(
                if (i < n) Color(0xFFFF3B6B) else Color.White.copy(alpha = 0.15f),
                tween(400),
                label = "life-$i",
            )
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** Pause button. */
@Composable
private fun PauseButton(accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, accent.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick)
            .drawBehind {
                val bw = size.width * 0.13f
                val bh = size.height * 0.38f
                val gap = size.width * 0.1f
                val top = (size.height - bh) / 2f
                drawRoundRect(Color.White, Offset(size.width / 2f - gap - bw, top), Size(bw, bh), CornerRadius(bw / 3f))
                drawRoundRect(Color.White, Offset(size.width / 2f + gap, top), Size(bw, bh), CornerRadius(bw / 3f))
            },
    )
}
