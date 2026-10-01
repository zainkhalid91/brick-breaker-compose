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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zainkhalid.brickbreaker.engine.BossKind
import com.zainkhalid.brickbreaker.engine.BrickEngine
import com.zainkhalid.brickbreaker.engine.BrickLevels
import com.zainkhalid.brickbreaker.engine.BrickPhase
import com.zainkhalid.brickbreaker.engine.BrickTheme
import com.zainkhalid.brickbreaker.render.BrickPalette
import kotlinx.coroutines.delay

/** Banners, boss bar, toasts and dialogs on top of the playfield. */
@Composable
fun BoxScope.BrickOverlays(engine: BrickEngine, theme: BrickTheme, onExit: () -> Unit) {
    val phase = engine.phase
    val accent = BrickPalette.accent(theme)
    val accent2 = BrickPalette.accent2(theme)

    AnimatedVisibility(
        visible = phase == BrickPhase.Intro,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(tween(250)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.5f),
        exit = fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 1.5f),
        label = "level-banner",
    ) {
        val boss = BrickLevels[engine.levelIndex].boss
        if (boss != null) BossBanner(boss) else LevelBanner(engine.levelIndex, accent, accent2)
    }

    val boss = engine.bossKind
    AnimatedVisibility(
        visible = boss != null && (phase == BrickPhase.Intro || phase == BrickPhase.Playing),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
        enter = fadeIn(tween(400)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) { -it },
        exit = fadeOut(tween(300)),
        label = "boss-bar",
    ) {
        if (boss != null) BossHealthBar(boss, health = { engine.bossHealth }, stage = engine.bossStage)
    }
    EnrageToast(engine, Modifier.align(Alignment.Center))

    AnimatedVisibility(
        visible = phase == BrickPhase.Playing && engine.serving && !engine.paused,
        // Below the paddle.
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(200)),
        label = "serve-hint",
    ) {
        ServeHint()
    }

    PickupToast(engine, Modifier.align(Alignment.TopCenter).padding(top = 64.dp))

    AnimatedVisibility(
        visible = phase == BrickPhase.LevelClear,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(tween(300)) + slideInVertically(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) { it / 2 },
        exit = fadeOut(tween(300)),
        label = "stage-clear",
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (BrickLevels[engine.levelIndex].boss != null) "BOSS DEFEATED" else "STAGE CLEAR",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, accent)),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                ),
            )
            StarRow(stars = engine.lastStars, animate = true)
            Text(
                "BONUS +${engine.levelBonus}",
                color = accent2,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
    }

    ModalCard(
        visible = engine.paused && phase != BrickPhase.GameOver && phase != BrickPhase.Victory,
        title = "PAUSED",
        subtitle = "SCORE ${engine.score} · PROGRESS SAVED",
        accent = accent,
        primary = "RESUME" to { engine.paused = false },
        secondary = "RETRY LEVEL" to { engine.retryLevel() },
        tertiary = "MENU" to onExit,
    )
    ModalCard(
        visible = phase == BrickPhase.GameOver,
        title = "GAME OVER",
        subtitle = "LEVEL ${engine.levelIndex + 1} · YOUR PROGRESS IS SAVED",
        accent = Color(0xFFFF3B5C),
        primary = "RETRY LEVEL" to { engine.retryLevel() },
        secondary = "MENU" to onExit,
    )
    ModalCard(
        visible = phase == BrickPhase.Victory,
        title = "YOU WIN!",
        subtitle = "${BrickLevels.size} LEVELS · ${BrickLevels.count { it.boss != null }} BOSSES · SCORE ${engine.score}",
        accent = Color(0xFFFFD132),
        primary = "PLAY AGAIN" to { engine.newGame() },
        secondary = "MENU" to onExit,
    )
}

/** Star rating, pops in one by one. */
@Composable
fun StarRow(stars: Int, animate: Boolean, size: Dp = 44.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.15f)) {
        for (i in 0 until 3) {
            val earned = i < stars
            val pop = remember { Animatable(if (animate) 0f else 1f) }
            LaunchedEffect(earned) {
                if (animate) {
                    delay(250L + 220L * i)
                    pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
                }
            }
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (earned) Color(0xFFFFD132) else Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val k = if (earned) pop.value else 1f
                        scaleX = k
                        scaleY = k
                    },
            )
        }
    }
}

/** Level intro banner. */
@Composable
private fun LevelBanner(index: Int, accent: Color, accent2: Color) {
    val theme = BrickLevels[index].theme
    val newArea = index == 0 || BrickLevels[index - 1].theme != theme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            (if (newArea) "NEW AREA · " else "") + theme.region,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier
                .background(accent.copy(alpha = 0.18f), RoundedCornerShape(50))
                .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 5.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "LEVEL ${index + 1}",
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color.White, accent)),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            ),
        )
        Text(
            BrickLevels[index].name.uppercase(),
            color = accent2,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )
    }
}

/** Boss intro banner. */
@Composable
private fun BossBanner(boss: BossKind) {
    val transition = rememberInfiniteTransition(label = "boss-banner")
    val scroll by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "boss-stripes",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "boss-warning",
    )
    val red = Color(0xFFFF2B3A)
    val final = boss == BossKind.VoidHeart
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .drawWithCache {
                    val stripe = Path()
                    val step = size.height * 1.4f
                    onDrawBehind {
                        drawRect(Color.Black.copy(alpha = 0.75f))
                        var x = -step * 2f + scroll * step
                        while (x < size.width + step) {
                            stripe.reset()
                            stripe.moveTo(x, size.height)
                            stripe.lineTo(x + step * 0.5f, size.height)
                            stripe.lineTo(x + step * 0.5f + size.height, 0f)
                            stripe.lineTo(x + size.height, 0f)
                            stripe.close()
                            drawPath(stripe, Color(0xFFFFC928))
                            x += step
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (final) "FINAL BOSS" else "WARNING",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .graphicsLayer { alpha = pulse },
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            boss.title,
            maxLines = 1,
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFB0B0), red)),
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                shadow = Shadow(red, blurRadius = 24f),
            ),
        )
        Text(
            boss.epithet,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )
    }
}

/** Boss health bar. The pale part drains behind the red one. */
@Composable
private fun BossHealthBar(boss: BossKind, health: () -> Float, stage: Int) {
    val target = health()
    val bar by animateFloatAsState(target, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium), label = "boss-hp")
    val ghost by animateFloatAsState(target, tween(700, delayMillis = 350, easing = FastOutSlowInEasing), label = "boss-hp-ghost")
    val red = when (stage) {
        1 -> Color(0xFFFF4E5E)
        2 -> Color(0xFFFF7A2E)
        else -> Color(0xFFFF2B3A)
    }
    Column(Modifier.fillMaxWidth(0.86f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                boss.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            for (i in 1..3) {
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(7.dp)
                        .background(if (i <= stage) red else Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2f)
                    drawRoundRect(Color.Black.copy(alpha = 0.65f), cornerRadius = r)
                    drawRoundRect(Color(0xFFFFE6C8), size = Size(size.width * ghost, size.height), cornerRadius = r, alpha = 0.85f)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(lerp(red, Color.White, 0.45f), red, lerp(red, Color.Black, 0.35f))),
                        size = Size(size.width * bar, size.height),
                        cornerRadius = r,
                    )
                    // Stage marks
                    for (f in floatArrayOf(0.33f, 0.66f)) {
                        drawRect(Color.Black.copy(alpha = 0.6f), Offset(size.width * f - 1f, 0f), Size(2f, size.height))
                    }
                    drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = r, style = Stroke(width = 1.5f))
                },
        )
    }
}

/** "ENRAGED" / "FINAL FORM" flash when the boss enters stage 2 or 3. */
@Composable
private fun EnrageToast(engine: BrickEngine, modifier: Modifier) {
    val stage = engine.bossStage
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(stage) {
        if (stage <= 1) return@LaunchedEffect
        visible = true
        delay(1300)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), initialScale = 2.2f),
        exit = fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 0.8f),
        label = "enrage",
    ) {
        Text(
            if (stage >= 3) "FINAL FORM" else "ENRAGED",
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFF2B3A))),
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                shadow = Shadow(Color(0xFFFF2B3A), blurRadius = 30f),
            ),
        )
    }
}

/** Serve hint. */
@Composable
private fun ServeHint() {
    val pulse = rememberInfiniteTransition(label = "serve")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "serve-alpha",
    )
    Text(
        "DRAG TO MOVE · RELEASE TO LAUNCH",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.graphicsLayer { alpha = pulseAlpha },
    )
}

/** Shows the name of a picked up power-up. */
@Composable
private fun PickupToast(engine: BrickEngine, modifier: Modifier) {
    val serial = engine.pickupSerial
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        visible = true
        delay(1100)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)) + slideInVertically(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) { -it },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 2 },
        label = "pickup",
    ) {
        val p = engine.lastPickup
        if (p != null) {
            val color = BrickPalette.powerUp(p)
            Text(
                p.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .background(color.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .border(1.5.dp, color, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}

/** Dialog with up to three buttons. */
@Composable
private fun BoxScope.ModalCard(
    visible: Boolean,
    title: String,
    subtitle: String,
    accent: Color,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    tertiary: Pair<String, () -> Unit>? = null,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200)),
        label = "modal-$title",
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC05060C))
                // Eat touches so the paddle doesn't move underneath.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.animateEnterExit(
                    enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.7f),
                    exit = scaleOut(tween(200), targetScale = 0.9f),
                ),
            ) {
                Text(
                    title,
                    style = TextStyle(
                        brush = Brush.verticalGradient(listOf(Color.White, accent)),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    ),
                )
                Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(12.dp))
                BrickPillButton(primary.first, accent, primary.second, Modifier.width(220.dp))
                BrickPillButton(secondary.first, Color(0xFFDDE3EE), secondary.second, Modifier.width(220.dp))
                if (tertiary != null) {
                    BrickPillButton(tertiary.first, Color(0xFF8C96A8), tertiary.second, Modifier.width(220.dp))
                }
            }
        }
    }
}
