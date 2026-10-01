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

package com.zainkhalid.brickbreaker

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zainkhalid.brickbreaker.data.BrickSaveStore
import com.zainkhalid.brickbreaker.engine.BrickEngine
import com.zainkhalid.brickbreaker.engine.BrickLevels
import com.zainkhalid.brickbreaker.engine.BrickPhase
import com.zainkhalid.brickbreaker.render.BrickAssets
import com.zainkhalid.brickbreaker.render.BrickPalette
import com.zainkhalid.brickbreaker.render.drawBrickWorld
import com.zainkhalid.brickbreaker.ui.BrickHud
import com.zainkhalid.brickbreaker.ui.BrickMenuScreen
import com.zainkhalid.brickbreaker.ui.BrickOverlays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 1:1 finger-to-paddle tracking; raise above 1 for a faster paddle. */
private const val DRAG_SENSITIVITY = 1.15f

private enum class BrickScreen { Menu, Game }

/** Entry point: menu and game screens. */
@Composable
fun BrickBreakerApp() {
    val context = LocalContext.current
    val store = remember { BrickSaveStore(context) }
    var screen by rememberSaveable { mutableStateOf(BrickScreen.Menu) }
    // Only true until the engine starts, so a recreated screen resumes the save.
    var freshStart by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(store.load()) }
    var best by remember { mutableIntStateOf(store.best) }
    var stars by remember { mutableStateOf(store.stars()) }
    GameWindowEffects()
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(300)) },
        label = "brick-screen",
    ) { target ->
        when (target) {
            BrickScreen.Menu -> BrickMenuScreen(
                best = best,
                saved = saved,
                stars = stars,
                onContinue = {
                    freshStart = false
                    screen = BrickScreen.Game
                },
                onNewGame = {
                    store.clear()
                    freshStart = true
                    screen = BrickScreen.Game
                },
            )
            BrickScreen.Game -> BrickGameScreen(
                store = store,
                freshStart = freshStart,
                onStarted = { freshStart = false },
                onExit = {
                    saved = store.load()
                    best = store.best
                    stars = store.stars()
                    screen = BrickScreen.Menu
                },
            )
        }
    }
}

/** HUD on top, playfield below. */
@Composable
private fun BrickGameScreen(
    store: BrickSaveStore,
    freshStart: Boolean,
    onStarted: () -> Unit,
    onExit: () -> Unit,
) {
    val engine = remember {
        BrickEngine().apply {
            val save = if (freshStart) null else store.load()
            if (save != null) restore(save) else newGame()
        }
    }
    LaunchedEffect(Unit) { onStarted() }
    val haptics = LocalHapticFeedback.current
    SideEffect {
        engine.onHaptic = { strong ->
            haptics.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        }
    }
    val phase = engine.phase
    val levelIndex = engine.levelIndex
    LaunchedEffect(phase, levelIndex) {
        when (phase) {
            BrickPhase.GameOver -> {
                // Keep the save so CONTINUE goes back to this level.
                store.best = engine.score
                store.save(engine.exportSave())
            }
            BrickPhase.Victory -> {
                store.best = engine.score
                store.clear()
            }
            BrickPhase.LevelClear -> {
                store.recordStars(levelIndex, engine.lastStars)
                store.best = engine.score
                store.save(engine.exportSave())
            }
            BrickPhase.Intro -> store.save(engine.exportSave())
            BrickPhase.Playing -> Unit
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (engine.phase == BrickPhase.Playing || engine.phase == BrickPhase.Intro) engine.paused = true
        store.save(engine.exportSave())
    }
    val exit = {
        store.save(engine.exportSave())
        onExit()
    }
    BackHandler {
        when {
            engine.phase == BrickPhase.GameOver || engine.phase == BrickPhase.Victory -> exit()
            engine.paused -> exit()
            else -> engine.paused = true
        }
    }

    val theme = BrickLevels[engine.levelIndex].theme
    Column(Modifier.fillMaxSize().background(BrickPalette.Night)) {
        BrickHud(engine = engine, theme = theme, onPause = { engine.paused = true })
        BrickPlayfield(engine = engine, onExit = exit, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/** Background, game canvas and overlays. */
@Composable
private fun BrickPlayfield(engine: BrickEngine, onExit: () -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val bottomInset = WindowInsets.navigationBars.getBottom(density)

    val assets by produceState<BrickAssets?>(null, size, density) {
        if (size.width > 0 && size.height > 0) {
            value = withContext(Dispatchers.Default) { BrickAssets.bake(size.width, size.height, density, textMeasurer) }
        }
    }
    LaunchedEffect(size, bottomInset) { engine.setViewport(size.width, size.height, bottomInset) }

    val tick = remember { mutableLongStateOf(0L) }
    val ready = assets
    if (ready != null) {
        LaunchedEffect(engine) {
            val driver = BrickFrameDriver(engine, tick)
            while (isActive) withFrameNanos(driver)
        }
    }

    val theme = BrickLevels[engine.levelIndex].theme
    val background by produceState<ImageBitmap?>(null, ready, theme) {
        if (ready != null) value = withContext(Dispatchers.Default) { ready.background(theme) }
    }
    val phase = engine.phase
    LaunchedEffect(ready, phase, engine.levelIndex) {
        val next = BrickLevels.getOrNull(engine.levelIndex + 1)?.theme
        if (ready != null && phase == BrickPhase.LevelClear && next != null) {
            withContext(Dispatchers.Default) { ready.background(next) }
        }
    }
    Box(modifier.onSizeChanged { size = it }) {
        if (ready == null) {
            Text(
                "LOADING",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            val sprites = ready.sprites(theme)
            val boss = engine.bossKind?.let { ready.boss(it) }
            Crossfade(targetState = background, animationSpec = tween(900), label = "plane") { image ->
                Spacer(Modifier.fillMaxSize().drawBehind { if (image != null) drawImage(image) })
            }
            Spacer(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { }
                    .pointerInput(engine) { paddleGestures(engine) }
                    .drawBehind {
                        tick.longValue
                        drawBrickWorld(engine, sprites, boss)
                    },
            )
            BrickOverlays(engine = engine, theme = theme, onExit = onExit)
        }
    }
}

/** Relative drag moves the paddle, lifting the finger launches. */
private suspend fun PointerInputScope.paddleGestures(engine: BrickEngine) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val id = down.id
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == id } ?: break
            if (!change.pressed) {
                engine.release()
                break
            }
            val dx = change.position.x - change.previousPosition.x
            if (dx != 0f) engine.dragBy(dx * DRAG_SENSITIVITY)
            change.consume()
        }
    }
}

/** Frame callback. One instance, reused every frame. */
private class BrickFrameDriver(
    private val engine: BrickEngine,
    private val tick: MutableLongState,
) : (Long) -> Unit {
    private var last = -1L

    override fun invoke(frameTimeNanos: Long) {
        if (last >= 0L) engine.update((frameTimeNanos - last) / 1_000_000_000f)
        last = frameTimeNanos
        tick.longValue = frameTimeNanos
    }
}

/** Light status bar icons and keep the screen on while playing. */
@Composable
private fun GameWindowEffects() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }
}
