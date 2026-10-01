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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zainkhalid.brickbreaker.engine.BrickLevels
import com.zainkhalid.brickbreaker.engine.BrickSave
import com.zainkhalid.brickbreaker.engine.PowerUp
import com.zainkhalid.brickbreaker.render.BrickPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MenuNeonYellow = Color(0xFFFFC928)
private val MenuNeonGreen = Color(0xFF7CFF3A)
private val MenuPlayCyan = Color(0xFF2FD8F5)

/** Menu brick stairs, (col, row) pairs. */
private val LeftStair = floatArrayOf(0f, 0f, 1f, 0f, 2f, 0f, 0f, 1f, 1f, 1f, 0f, 2f, 1f, 2f, 0f, 3f)
private val RightStair = floatArrayOf(1f, 0f, 2f, 0f, 0.5f, 1f, 1.5f, 1f, 1f, 2f, 2f, 2f, 2f, 3f, 2f, 4f)

/** Title screen. */
@Composable
fun BrickMenuScreen(
    best: Int,
    saved: BrickSave?,
    stars: List<Int>,
    unlocked: Int,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onPlayLevel: (Int) -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "menu")
    val comet by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "menu-comet",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "menu-shimmer",
    )
    var confirmNew by remember { mutableStateOf(false) }
    val drops = remember { List(3) { Animatable(-400f) } }
    LaunchedEffect(Unit) {
        drops.forEachIndexed { i, a ->
            launch {
                delay(120L * i)
                a.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 220f))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0F2E), Color(0xFF131A48), Color(0xFF0A0F2E))))
            .drawWithCache {
                val brick = Size(size.width * 0.105f, size.width * 0.052f)
                val gap = size.width * 0.006f
                val leftOrigin = Offset(-brick.width * 0.4f, size.height * 0.47f)
                val rightOrigin = Offset(size.width - brick.width * 2.6f, size.height * 0.7f)
                val corner = CornerRadius(brick.height * 0.18f)
                val dot = Color.White.copy(alpha = 0.035f)
                val step = size.width / 18f
                onDrawBehind {
                    // Dots
                    var gx = step / 2f
                    while (gx < size.width) {
                        var gy = step / 2f
                        while (gy < size.height) {
                            drawCircle(dot, 1.6f, Offset(gx, gy))
                            gy += step
                        }
                        gx += step
                    }
                    // Brick stairs, each brick drawn 3x for the glow.
                    for (side in 0..1) {
                        val cells = if (side == 0) LeftStair else RightStair
                        val origin = if (side == 0) leftOrigin else rightOrigin
                        val color = if (side == 0) MenuNeonYellow else MenuNeonGreen
                        var k = 0
                        while (k < cells.size) {
                            val tl = Offset(
                                origin.x + cells[k] * (brick.width + gap),
                                origin.y + cells[k + 1] * (brick.height + gap),
                            )
                            drawRoundRect(color, Offset(tl.x - 6f, tl.y - 6f), Size(brick.width + 12f, brick.height + 12f), corner, alpha = 0.12f * shimmer)
                            drawRoundRect(color, Offset(tl.x - 2.5f, tl.y - 2.5f), Size(brick.width + 5f, brick.height + 5f), corner, alpha = 0.3f * shimmer)
                            drawRoundRect(color, tl, brick, corner)
                            drawRoundRect(Color.White, tl, Size(brick.width, brick.height * 0.28f), corner, alpha = 0.35f)
                            k += 2
                        }
                    }
                    // Comet
                    val sx = size.width * 0.58f
                    val sy = size.height * 0.28f
                    val ex = size.width * 0.52f
                    val ey = size.height * 0.46f
                    val t = comet
                    for (i in 0 until 16) {
                        val f = (t - i * 0.012f).coerceIn(0f, 1f)
                        val a = 1f - i / 16f
                        drawCircle(
                            Color.White,
                            radius = 7f * a + 1f,
                            center = Offset(sx + (ex - sx) * f, sy + (ey - sy) * f),
                            alpha = a * 0.8f * (1f - t * 0.6f),
                            blendMode = BlendMode.Plus,
                        )
                    }
                }
            }
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrickTitleWord(
                "BRICK",
                Brush.verticalGradient(listOf(Color(0xFFFF8FC4), Color(0xFFFF2E7E), Color(0xFFD1105A))),
                60.sp,
                Color(0xFF7A0B3A),
                Modifier.graphicsLayer { translationY = drops[0].value },
            )
            BrickTitleWord(
                "BREAKER",
                Brush.verticalGradient(listOf(Color(0xFFFFF1A0), Color(0xFFFFC928), Color(0xFFFF8A1E))),
                56.sp,
                Color(0xFF8A3B00),
                Modifier.graphicsLayer { translationY = drops[1].value },
            )
            BrickTitleWord(
                "RETRO",
                Brush.verticalGradient(listOf(Color(0xFFB8FBFF), Color(0xFF2FD8F5), Color(0xFF1592C9))),
                38.sp,
                Color(0xFF0B4A6B),
                Modifier.graphicsLayer { translationY = drops[2].value },
            )

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (saved != null) {
                    BrickPillButton("CONTINUE", MenuPlayCyan, onContinue, Modifier.width(220.dp))
                    Text(
                        "LEVEL ${saved.levelIndex + 1} · ${BrickLevels[saved.levelIndex].name.uppercase()} · SCORE ${saved.score}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    BrickPillButton("NEW GAME", Color(0xFFDDE3EE), { confirmNew = true }, Modifier.width(220.dp))
                } else {
                    BrickPillButton("PLAY", MenuPlayCyan, onNewGame, Modifier.width(220.dp))
                }
                LevelPicker(stars, unlocked, onPlayLevel)
                PowerUpLegend()
                Text(
                    if (best > 0) "BEST  $best" else "${BrickLevels.size} LEVELS · ${BrickLevels.count { it.boss != null }} BOSSES · 3 LIVES",
                    color = if (best > 0) MenuNeonYellow else Color.White.copy(alpha = 0.7f),
                    fontSize = if (best > 0) 14.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                )
            }
        }
        if (saved != null) {
            NewGameConfirm(
                visible = confirmNew,
                saved = saved,
                onConfirm = {
                    confirmNew = false
                    onNewGame()
                },
                onCancel = { confirmNew = false },
            )
        }
    }
}

/** Confirm before a new game wipes the save. */
@Composable
private fun BoxScope.NewGameConfirm(visible: Boolean, saved: BrickSave, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200)),
        label = "new-game-confirm",
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xD905060C))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.75f),
                        exit = scaleOut(tween(200), targetScale = 0.9f),
                    )
                    .fillMaxWidth(0.86f)
                    .background(Color(0xFF131A48), RoundedCornerShape(24.dp))
                    .border(1.5.dp, Color(0xFFFF4E5E).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Text(
                    "START OVER?",
                    style = TextStyle(
                        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFF4E5E))),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    ),
                )
                Text(
                    "Starting a new game will reset all your current progress.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "YOU ARE ON LEVEL ${saved.levelIndex + 1} · ${BrickLevels[saved.levelIndex].name.uppercase()} · SCORE ${saved.score}",
                    color = MenuNeonYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                BrickPillButton("KEEP PLAYING", MenuPlayCyan, onCancel, Modifier.width(220.dp))
                BrickPillButton("START OVER", Color(0xFFFF4E5E), onConfirm, Modifier.width(220.dp))
            }
        }
    }
}

/** Level grid. Unlocked levels can be tapped to play them again; locked ones show a padlock. */
@Composable
private fun LevelPicker(stars: List<Int>, unlocked: Int, onPlayLevel: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (rowStart in BrickLevels.indices step 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in rowStart until minOf(rowStart + 5, BrickLevels.size)) {
                    LevelTile(i, stars.getOrElse(i) { 0 }, open = i <= unlocked, onClick = { onPlayLevel(i) })
                }
            }
        }
        Text(
            "TAP A LEVEL TO PLAY IT",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    }
}

/** One level in the picker. */
@Composable
private fun LevelTile(index: Int, stars: Int, open: Boolean, onClick: () -> Unit) {
    val boss = BrickLevels[index].boss != null
    val tint = if (boss) Color(0xFFFF4E5E) else MenuPlayCyan
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(width = 58.dp, height = 44.dp)
            .background(if (open) tint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .border(1.dp, if (open) tint.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .clickable(enabled = open, onClick = onClick)
            .padding(top = 3.dp),
    ) {
        if (open) {
            Text(
                if (boss) "BOSS ${index + 1}" else "${index + 1}",
                color = if (boss) tint else Color.White,
                fontSize = if (boss) 10.sp else 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.weight(1f))
            StarRow(stars = stars, animate = false, size = 10.dp)
            Spacer(Modifier.height(4.dp))
        } else {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Power-up legend. */
@Composable
private fun PowerUpLegend() {
    val bonuses = PowerUp.entries.filter { !it.curse }
    val curses = PowerUp.entries.filter { it.curse }
    Column(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LegendGrid(bonuses)
        Text(
            "AVOID",
            color = Color(0xFFFF3355),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        LegendGrid(curses)
    }
}

/** Three-column grid of legend items. */
@Composable
private fun LegendGrid(items: List<PowerUp>) {
    for (rowIndex in 0 until (items.size + 2) / 3) {
        Row(Modifier.fillMaxWidth()) {
            for (k in 0..2) {
                val i = rowIndex * 3 + k
                Box(Modifier.weight(1f)) {
                    if (i < items.size) LegendItem(items[i])
                }
            }
        }
    }
}

/** One legend row. */
@Composable
private fun LegendItem(p: PowerUp) {
    val color = BrickPalette.powerUp(p)
    val chip = if (p.curse) {
        Modifier
            .background(BrickPalette.CurseBody, RoundedCornerShape(50))
            .border(1.dp, color, RoundedCornerShape(50))
    } else {
        Modifier.background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.45f), color)), RoundedCornerShape(50))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 28.dp, height = 14.dp).then(chip), contentAlignment = Alignment.Center) {
            Text(p.glyph, color = if (p.curse) color else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(5.dp))
        Text(
            p.title,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
