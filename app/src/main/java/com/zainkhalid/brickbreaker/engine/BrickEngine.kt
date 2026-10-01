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

package com.zainkhalid.brickbreaker.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// World geometry (world units; 1000 units = screen width)
const val WORLD_W = 1000f
const val GRID_COLS = 13
const val GRID_ROWS_MAX = 14
const val GRID_LEFT = 30f
const val GRID_TOP = 64f
const val CELL_W = (WORLD_W - 2 * GRID_LEFT) / GRID_COLS
const val CELL_H = 38f
const val BRICK_GAP = 4f
const val WALL = 16f
const val CEILING = 12f
const val BALL_R = 11f
const val PADDLE_H = 26f
const val PADDLE_W_BASE = 176f
const val PADDLE_W_WIDE = 280f
const val PADDLE_W_SMALL = 112f

/** Paddle sits a little below the middle of the playfield. */
const val PADDLE_Y_FRAC = 0.63f
const val SHIELD_DROP = 110f
const val DROP_W = 66f
const val DROP_H = 28f
const val BOLT_LEN = 30f
const val DRONE_R = 21f
const val MOVER_H = 20f

// Pool sizes (everything is preallocated)
const val MAX_BALLS = 12
const val TRAIL_LEN = 14
const val MAX_DROPS = 16
const val MAX_BOLTS = 48
const val MAX_PARTICLES = 800
const val MAX_RINGS = 28
const val MAX_DRONES = 6
const val MAX_MOVERS = 3
const val START_LIVES = 3
const val MAX_LIVES = 5
const val BLAST_CHARGES = 3

// Particle colour slots beyond the brick kinds
const val PART_SPARK = 12
const val PART_FIRE = 13
const val PART_GOLD = 14
const val PART_LASER = 15
const val PART_DRONE = 16
const val PART_CURSE = 17
const val PART_LEAF = 18
const val PART_SAND = 19
const val PART_TEAL = 20
const val PART_PLASMA = 21
const val PART_VOID = 22
const val PART_COLOR_COUNT = 23

const val RING_LIFE = 0.45f
const val INTRO_TIME = 1.7f
const val BOSS_INTRO_TIME = 2.8f

// Tuning
private const val MAX_DT = 1f / 20f
private const val MAX_STEP_DIST = 4f
private const val MAX_SUBSTEPS = 64
private const val CLEAR_TIME = 3.2f
private const val DROP_CHANCE = 0.18f
private const val DRONE_DROP_CHANCE = 0.45f
private const val DROP_SPEED = 290f
private const val BOLT_SPEED = 1500f
private const val LASER_COOLDOWN = 0.3f
private const val MAX_BOUNCE = 1.15f
private const val SPIN_GAIN = 0.00022f
private const val MAX_SPIN = 0.25f
private const val MIN_VERTICAL = 0.28f
private const val LAUNCH_SPREAD = 0.75f
private const val MIN_LAUNCH = 0.14f
private const val SPLIT_ANGLE = 0.38f
private const val SPEED_GAIN_PER_HIT = 1.012f
private const val SPEED_CAP = 1.45f
private const val RAMP_PER_20S = 0.035f
private const val RAMP_CAP = 0.3f
private const val SLOW_FACTOR = 0.55f
private const val RUSH_FACTOR = 1.4f
private const val GRAVITY = 900f
private const val TRAIL_INTERVAL = 1f / 90f
private const val DRONE_SPEED = 55f
private const val DRONE_SWAY = 110f
private const val DRONE_POINTS = 150
private const val STUN_TIME = 1.1f
private const val SHOT_STUN_TIME = 0.9f
private const val BEAM_STUN_TIME = 1.3f
private const val REFLECT_SPEED = 900f
private const val REFLECT_DAMAGE = 3f
private const val LASER_DAMAGE = 0.35f
private const val GRAVITY_RADIUS = 560f
private const val MAX_MINIONS = 4
private const val BOSS_DROP_CHANCE = 0.14f
private const val SEED_FALL = 260f
private const val TWO_PI = (PI * 2.0).toFloat()

/** Lifecycle of a run, observed by the overlay composables. */
enum class BrickPhase { Intro, Playing, LevelClear, GameOver, Victory }

/** Game simulation. All state lives in preallocated arrays, nothing allocates per frame. */
class BrickEngine(seed: Int = 1991) {

    private val rng = Random(seed)
    private val powerUps = PowerUp.entries
    private val goodWeight = powerUps.filter { !it.curse }.sumOf { it.weight }
    private val curseWeight = powerUps.filter { it.curse }.sumOf { it.weight }
    private var level: BrickLevel = BrickLevels[0]

    // Compose-observable, low-frequency state
    var phase by mutableStateOf(BrickPhase.Intro)
        private set
    var levelIndex by mutableIntStateOf(0)
        private set
    var score by mutableIntStateOf(0)
        private set
    var lives by mutableIntStateOf(START_LIVES)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var serving by mutableStateOf(true)
        private set
    var paused by mutableStateOf(false)
    var lastPickup by mutableStateOf<PowerUp?>(null)
        private set
    var pickupSerial by mutableIntStateOf(0)
        private set
    var levelBonus by mutableIntStateOf(0)
        private set
    var lastStars by mutableIntStateOf(0)
        private set

    /** Boss health 0..1, only updated on hits. */
    var bossHealth by mutableFloatStateOf(0f)
        private set

    /** Boss stage 1..3; bumps when the boss enrages. */
    var bossStage by mutableIntStateOf(1)
        private set

    /** The boss of the current level, or `null`. */
    val bossKind: BossKind? get() = level.boss

    /** Haptic hook, installed by the UI. */
    var onHaptic: (strong: Boolean) -> Unit = {}

    // Viewport
    var pxPerUnit = 1f
        private set
    var worldH = 1800f
        private set
    private var bottomInset = 0f
    val paddleTop: Float get() = worldH * PADDLE_Y_FRAC
    val shieldY: Float get() = paddleTop + SHIELD_DROP

    /** Lowest visible world line, above the navigation bar. */
    val floorY: Float get() = worldH - bottomInset

    // Bricks
    val brickKind = IntArray(GRID_COLS * GRID_ROWS_MAX) { -1 }
    val brickHp = IntArray(GRID_COLS * GRID_ROWS_MAX)
    val brickMaxHp = IntArray(GRID_COLS * GRID_ROWS_MAX)
    val brickFlash = FloatArray(GRID_COLS * GRID_ROWS_MAX)
    var rows = 0
        private set
    var gridTop = GRID_TOP
        private set
    private var creep = 0f
    private var breakableLeft = 0

    // Paddle
    var paddleX = WORLD_W / 2f
        private set
    var paddleW = PADDLE_W_BASE
        private set
    var paddleGlow = 0f
        private set
    var paddleStun = 0f
        private set
    private var paddleTarget = WORLD_W / 2f
    private var paddleVel = 0f

    // Balls + trails
    val ballX = FloatArray(MAX_BALLS)
    val ballY = FloatArray(MAX_BALLS)
    val ballDx = FloatArray(MAX_BALLS)
    val ballDy = FloatArray(MAX_BALLS)
    val ballAlive = BooleanArray(MAX_BALLS)
    val ballStuck = BooleanArray(MAX_BALLS)
    private val ballSwing = BooleanArray(MAX_BALLS)
    private val ballStuckOffset = FloatArray(MAX_BALLS)
    private val splitMask = BooleanArray(MAX_BALLS)
    val trailX = FloatArray(MAX_BALLS * TRAIL_LEN)
    val trailY = FloatArray(MAX_BALLS * TRAIL_LEN)
    val trailHead = IntArray(MAX_BALLS)
    val trailCount = IntArray(MAX_BALLS)
    private var trailClock = 0f
    private var baseSpeed = 800f
    private var ballSpeed = 800f
    var blastCharges = 0
        private set

    // Capsules + laser bolts
    val dropX = FloatArray(MAX_DROPS)
    val dropY = FloatArray(MAX_DROPS)
    val dropType = IntArray(MAX_DROPS)
    val dropAlive = BooleanArray(MAX_DROPS)
    val boltX = FloatArray(MAX_BOLTS)
    val boltY = FloatArray(MAX_BOLTS)
    val boltAlive = BooleanArray(MAX_BOLTS)

    // Drones + sweeping bars
    val droneX = FloatArray(MAX_DRONES)
    val droneY = FloatArray(MAX_DRONES)
    val droneAlive = BooleanArray(MAX_DRONES)
    private val droneBaseX = FloatArray(MAX_DRONES)
    val droneAge = FloatArray(MAX_DRONES)
    private var droneClock = 0f
    val moverX = FloatArray(MAX_MOVERS)
    val moverY = FloatArray(MAX_MOVERS)
    val moverW = FloatArray(MAX_MOVERS)
    val moverFlash = FloatArray(MAX_MOVERS)
    private val moverSrc = IntArray(MAX_MOVERS)
    var moverCount = 0
        private set

    // Boss + its projectiles
    val boss = BrickBoss()
    val shotX = FloatArray(MAX_SHOTS)
    val shotY = FloatArray(MAX_SHOTS)
    private val shotVx = FloatArray(MAX_SHOTS)
    private val shotVy = FloatArray(MAX_SHOTS)
    val shotKind = IntArray(MAX_SHOTS)
    val shotAlive = BooleanArray(MAX_SHOTS)
    val shotBack = BooleanArray(MAX_SHOTS)
    val shotAge = FloatArray(MAX_SHOTS)

    // Particles + shock rings (ring buffers)
    val partX = FloatArray(MAX_PARTICLES)
    val partY = FloatArray(MAX_PARTICLES)
    private val partVx = FloatArray(MAX_PARTICLES)
    private val partVy = FloatArray(MAX_PARTICLES)
    val partLife = FloatArray(MAX_PARTICLES)
    val partMax = FloatArray(MAX_PARTICLES)
    val partSize = FloatArray(MAX_PARTICLES)
    val partColor = IntArray(MAX_PARTICLES)
    private var partCursor = 0
    val ringX = FloatArray(MAX_RINGS)
    val ringY = FloatArray(MAX_RINGS)
    val ringLife = FloatArray(MAX_RINGS)
    val ringRadius = FloatArray(MAX_RINGS)
    val ringColor = IntArray(MAX_RINGS)
    private var ringCursor = 0

    // Power-up timers + effects
    val timers = FloatArray(powerUps.size)
    var shieldCharges = 0
        private set
    private var laserClock = 0f
    private var slowMix = 0f
    private var rushMix = 0f
    var time = 0f
        private set
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set
    private var shake = 0f
    private var phaseClock = 0f
    private var levelTime = 0f
    private var livesLostThisLevel = 0
    private var levelStartScore = 0
    private var levelStartLives = START_LIVES
    private var introTime = INTRO_TIME

    val fireActive: Boolean get() = timers[PowerUp.Fire.ordinal] > 0f

    /** Intro progress 0..1, 1 when not in the intro. */
    val introProgress: Float
        get() = if (phase == BrickPhase.Intro) (1f - phaseClock / introTime).coerceIn(0f, 1f) else 1f

    fun isActive(p: PowerUp): Boolean = timers[p.ordinal] > 0f

    /** Remaining fraction of a timed power-up, for the HUD bars. */
    fun timerFraction(p: PowerUp): Float =
        if (p.duration <= 0f) 0f else (timers[p.ordinal] / p.duration).coerceIn(0f, 1f)

    /** Launch angle (radians from vertical) a stuck ball would leave at. */
    fun aimAngle(i: Int): Float {
        val off = (ballStuckOffset[i] / (paddleW * 0.5f)).coerceIn(-1f, 1f)
        val a = off * LAUNCH_SPREAD
        return if (abs(a) < MIN_LAUNCH) (if (off < 0f) -MIN_LAUNCH else MIN_LAUNCH) else a
    }

    // Public API

    /** Maps the playfield's pixel size into world units. */
    fun setViewport(widthPx: Int, heightPx: Int, bottomInsetPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        pxPerUnit = widthPx / WORLD_W
        worldH = heightPx / pxPerUnit
        bottomInset = bottomInsetPx / pxPerUnit
        layoutMovers()
    }

    /** Resets score and lives and starts level 1. */
    fun newGame() {
        startLevel(0)
    }

    /** Fresh start on [index] with zero score and full lives. Used for replaying a level. */
    fun startLevel(index: Int) {
        score = 0
        lives = START_LIVES
        resetRun()
        loadLevel(index.coerceIn(0, BrickLevels.lastIndex))
    }

    /** Restart the current level with the score and lives it started with. */
    fun retryLevel() {
        score = levelStartScore
        lives = max(levelStartLives, START_LIVES)
        resetRun()
        loadLevel(levelIndex)
    }

    /** Restores a checkpoint written by [exportSave]. */
    fun restore(save: BrickSave) {
        if (save.levelIndex !in BrickLevels.indices) {
            newGame()
            return
        }
        score = save.score.coerceAtLeast(0)
        lives = save.lives.coerceIn(1, MAX_LIVES)
        resetRun()
        loadLevel(save.levelIndex)
        val saved = save.bricks
        // Boss levels always start fresh.
        if (level.boss != null || saved == null || saved.size != rows * GRID_COLS) return
        breakableLeft = 0
        for (i in 0 until rows * GRID_COLS) {
            if (brickKind[i] < 0) continue
            val hp = saved[i]
            if (hp == 0) {
                brickKind[i] = -1
                brickHp[i] = 0
            } else if (brickHp[i] > 0) {
                brickHp[i] = hp.coerceIn(1, brickMaxHp[i])
                breakableLeft++
            }
        }
        if (breakableLeft == 0) {
            loadLevel(save.levelIndex)
            return
        }
        creep = save.creep.coerceIn(0f, level.maxCreep)
        gridTop = GRID_TOP + creep
    }

    /** Current save point, null once the game is won. */
    fun exportSave(): BrickSave? = when (phase) {
        BrickPhase.Victory -> null
        BrickPhase.GameOver -> BrickSave(levelIndex, levelStartScore, max(levelStartLives, START_LIVES), null, 0f)
        BrickPhase.LevelClear ->
            if (levelIndex + 1 < BrickLevels.size) BrickSave(levelIndex + 1, score, lives, null, 0f) else null
        else ->
            if (level.boss != null) BrickSave(levelIndex, levelStartScore, levelStartLives, null, 0f)
            else BrickSave(levelIndex, score, lives, brickHp.copyOf(rows * GRID_COLS), creep)
    }

    /** Relative paddle drag, in pixels. */
    fun dragBy(dxPx: Float) {
        if (paused || paddleStun > 0f || phase == BrickPhase.GameOver || phase == BrickPhase.Victory) return
        val half = paddleW * 0.5f
        val dx = if (isActive(PowerUp.Reverse)) -dxPx else dxPx
        paddleTarget = (paddleTarget + dx / pxPerUnit).coerceIn(WALL + half, WORLD_W - WALL - half)
    }

    /** Finger lifted: launches any ball resting on the paddle. */
    fun release() {
        if (phase == BrickPhase.Playing && !paused) launchStuck()
    }

    /** Advances the world by one vsync interval. */
    fun update(dtSeconds: Float) {
        if (paused) return
        val dt = dtSeconds.coerceIn(0f, MAX_DT)
        time += dt
        when (phase) {
            BrickPhase.Intro -> {
                simulate(dt)
                phaseClock -= dt
                if (phaseClock <= 0f) phase = BrickPhase.Playing
            }
            BrickPhase.Playing -> simulate(dt)
            BrickPhase.LevelClear -> {
                simulate(dt)
                phaseClock -= dt
                if (phaseClock <= 0f) advanceLevel()
            }
            BrickPhase.GameOver, BrickPhase.Victory -> Unit
        }
        updateEffects(dt)
    }

    // Level flow

    private fun resetRun() {
        paused = false
        partLife.fill(0f)
        ringLife.fill(0f)
        paddleX = WORLD_W / 2f
        paddleTarget = paddleX
    }

    private fun loadLevel(index: Int) {
        level = BrickLevels[index]
        levelIndex = index
        rows = level.layout.size
        brickKind.fill(-1)
        brickHp.fill(0)
        brickMaxHp.fill(0)
        brickFlash.fill(0f)
        breakableLeft = 0
        for (r in 0 until rows) {
            val line = level.layout[r]
            for (c in 0 until GRID_COLS) {
                val kind = BrickKind.fromChar(line[c])
                if (kind < 0) continue
                val i = r * GRID_COLS + c
                val hp = BrickKind.hitPoints(kind)
                brickKind[i] = kind
                brickHp[i] = hp
                brickMaxHp[i] = hp
                if (hp > 0) breakableLeft++
            }
        }
        creep = 0f
        gridTop = GRID_TOP
        baseSpeed = level.baseSpeed
        levelTime = 0f
        livesLostThisLevel = 0
        levelStartScore = score
        levelStartLives = lives
        boss.reset(level.boss)
        bossHealth = if (level.boss != null) 1f else 0f
        bossStage = 1
        shotAlive.fill(false)
        droneAlive.fill(false)
        droneClock = level.droneEvery
        layoutMovers()
        clearPowerUps()
        resetServe()
        combo = 0
        phase = BrickPhase.Intro
        introTime = if (level.boss != null) BOSS_INTRO_TIME else INTRO_TIME
        phaseClock = introTime
    }

    /** Put the moving bars below the bricks, skip any too close to the paddle. */
    private fun layoutMovers() {
        val movers = level.movers
        val bottom = GRID_TOP + rows * CELL_H + level.maxCreep
        moverCount = 0
        for (m in movers.indices) {
            val y = bottom + movers[m].gap
            if (y > paddleTop - 200f) continue
            moverSrc[moverCount] = m
            moverY[moverCount] = y
            moverW[moverCount] = movers[m].width
            moverCount++
        }
        positionMovers()
    }

    private fun positionMovers() {
        val movers = level.movers
        for (k in 0 until moverCount) {
            val m = movers[moverSrc[k]]
            val cx = WORLD_W / 2f + sin(time * m.speed + m.phase) * m.range
            moverX[k] = cx - m.width * 0.5f
        }
    }

    private fun advanceLevel() {
        if (levelIndex + 1 < BrickLevels.size) loadLevel(levelIndex + 1) else phase = BrickPhase.Victory
    }

    private fun levelCleared() {
        phase = BrickPhase.LevelClear
        phaseClock = CLEAR_TIME
        var stars = 1
        if (livesLostThisLevel == 0) stars++
        if (levelTime <= level.par) stars++
        lastStars = stars
        levelBonus = 1000 * (levelIndex + 1) + 250 * lives + 500 * stars + if (level.boss != null) 5000 else 0
        score += levelBonus
        for (i in 0 until MAX_BALLS) {
            if (!ballAlive[i]) continue
            burst(ballX[i], ballY[i], PART_SPARK, 18, 0f, 0f)
            ballAlive[i] = false
        }
        for (k in 0 until MAX_DRONES) if (droneAlive[k]) destroyDrone(k, reward = false)
        dropAlive.fill(false)
        boltAlive.fill(false)
        shotAlive.fill(false)
        timers.fill(0f)
        repeat(90) {
            emit(
                x = WALL + rng.nextFloat() * (WORLD_W - 2 * WALL),
                y = paddleTop * 0.7f,
                vx = (rng.nextFloat() - 0.5f) * 500f,
                vy = -500f - rng.nextFloat() * 700f,
                life = 1.2f + rng.nextFloat() * 0.9f,
                size = 7f + rng.nextFloat() * 7f,
                color = rng.nextInt(8),
            )
        }
        onHaptic(true)
    }

    private fun loseLife() {
        lives -= 1
        livesLostThisLevel++
        shake = 1.2f
        combo = 0
        onHaptic(true)
        clearPowerUps()
        shotAlive.fill(false)
        boss.calm()
        if (lives <= 0) {
            lives = 0
            phase = BrickPhase.GameOver
        } else {
            resetServe()
        }
    }

    private fun clearPowerUps() {
        timers.fill(0f)
        shieldCharges = 0
        blastCharges = 0
        slowMix = 0f
        rushMix = 0f
        paddleStun = 0f
        dropAlive.fill(false)
        boltAlive.fill(false)
    }

    private fun resetServe() {
        ballAlive.fill(false)
        ballStuck.fill(false)
        trailCount.fill(0)
        ballSpeed = baseSpeed
        ballAlive[0] = true
        ballStuck[0] = true
        ballSwing[0] = true
        ballStuckOffset[0] = 0f
        ballX[0] = paddleX
        ballY[0] = paddleTop - BALL_R - 0.5f
        ballDx[0] = 0f
        ballDy[0] = -1f
        serving = true
    }

    private fun launchStuck() {
        var launched = false
        for (i in 0 until MAX_BALLS) {
            if (!ballAlive[i] || !ballStuck[i]) continue
            val a = aimAngle(i)
            ballDx[i] = sin(a)
            ballDy[i] = -cos(a)
            ballStuck[i] = false
            ballSwing[i] = false
            launched = true
        }
        if (launched) serving = false
    }

    // Simulation

    private fun simulate(dt: Float) {
        val half = paddleW * 0.5f
        paddleTarget = paddleTarget.coerceIn(WALL + half, WORLD_W - WALL - half)
        val live = phase == BrickPhase.Playing && !serving

        // Ball speeds up and the wall creeps down the longer the level takes.
        if (live) {
            levelTime += dt
            val floor = baseSpeed * (1f + min(levelTime / 20f * RAMP_PER_20S, RAMP_CAP))
            if (ballSpeed < floor) ballSpeed = floor
            if (level.creepSpeed > 0f && creep < level.maxCreep) {
                creep = min(creep + level.creepSpeed * dt, level.maxCreep)
                gridTop = GRID_TOP + creep
            }
        }
        positionMovers()

        slowMix += ((if (isActive(PowerUp.Slow)) 1f else 0f) - slowMix) * (1f - exp(-6f * dt))
        rushMix += ((if (isActive(PowerUp.Rush)) 1f else 0f) - rushMix) * (1f - exp(-6f * dt))
        val speedMul = (1f - (1f - SLOW_FACTOR) * slowMix) * (1f + (RUSH_FACTOR - 1f) * rushMix)

        val travel = max(ballSpeed * speedMul, BOLT_SPEED) * dt
        val n = ceil(travel / MAX_STEP_DIST).toInt().coerceIn(1, MAX_SUBSTEPS)
        val h = dt / n
        val from = paddleX
        val to = paddleTarget
        for (s in 1..n) {
            paddleX = from + (to - from) * (s.toFloat() / n)
            step(h, speedMul)
            if (phase == BrickPhase.GameOver) break
        }
        paddleX = to
        paddleVel = if (dt > 0f) (to - from) / dt else 0f

        val widthTarget = when {
            isActive(PowerUp.Expand) -> PADDLE_W_WIDE
            isActive(PowerUp.Shrink) -> PADDLE_W_SMALL
            else -> PADDLE_W_BASE
        }
        paddleW += (widthTarget - paddleW) * (1f - exp(-10f * dt))

        for (k in timers.indices) if (timers[k] > 0f) timers[k] = max(0f, timers[k] - dt)
        if (paddleStun > 0f) {
            paddleStun = max(0f, paddleStun - dt)
            if (rng.nextFloat() < 0.5f) {
                emit(paddleX + (rng.nextFloat() - 0.5f) * paddleW, paddleTop, (rng.nextFloat() - 0.5f) * 300f, -200f, 0.25f, 5f, PART_DRONE)
            }
        }

        if (isActive(PowerUp.Laser) && phase == BrickPhase.Playing) {
            laserClock -= dt
            if (laserClock <= 0f) {
                fireBolts()
                laserClock = LASER_COOLDOWN
            }
        }

        stepDrones(dt, live)
        if (boss.active) {
            boss.update(dt, host, live)
            stepShots(dt)
            if (boss.defeated && phase == BrickPhase.Playing) levelCleared()
        }

        trailClock += dt
        if (trailClock >= TRAIL_INTERVAL) {
            trailClock = min(trailClock - TRAIL_INTERVAL, TRAIL_INTERVAL)
            recordTrails()
        }
    }

    private fun step(h: Float, speedMul: Float) {
        val move = ballSpeed * speedMul * h
        val top = paddleTop
        for (i in 0 until MAX_BALLS) {
            if (!ballAlive[i]) continue
            if (ballStuck[i]) {
                if (ballSwing[i]) ballStuckOffset[i] = sin(time * 2.4f) * paddleW * 0.32f
                ballX[i] = paddleX + ballStuckOffset[i]
                ballY[i] = top - BALL_R - 0.5f
                continue
            }
            val g = boss.gravity
            if (g > 0f) bend(i, g, h, move)
            var x = ballX[i] + ballDx[i] * move
            var y = ballY[i] + ballDy[i] * move
            if (x < WALL + BALL_R) {
                x = WALL + BALL_R
                ballDx[i] = abs(ballDx[i])
            } else if (x > WORLD_W - WALL - BALL_R) {
                x = WORLD_W - WALL - BALL_R
                ballDx[i] = -abs(ballDx[i])
            }
            if (y < CEILING + BALL_R) {
                y = CEILING + BALL_R
                ballDy[i] = abs(ballDy[i])
            }
            ballX[i] = x
            ballY[i] = y

            collideBricks(i)
            if (phase == BrickPhase.LevelClear) return
            if (boss.active) {
                collideBoss(i)
                if (!ballAlive[i]) continue
            }
            for (k in 0 until moverCount) {
                if (collideRect(i, moverX[k], moverY[k], moverX[k] + moverW[k], moverY[k] + MOVER_H)) moverFlash[k] = 1f
            }
            collideDrones(i)
            collidePaddle(i)

            if (shieldCharges > 0 && ballDy[i] > 0f && ballY[i] + BALL_R >= shieldY) {
                ballY[i] = shieldY - BALL_R
                ballDy[i] = -abs(ballDy[i])
                ensureAngle(i)
                shieldCharges--
                spawnRing(ballX[i], shieldY, PART_SPARK, 80f)
                burst(ballX[i], shieldY, PART_SPARK, 10, 0f, 0f)
                onHaptic(false)
            }

            if (ballY[i] - BALL_R > floorY) {
                ballAlive[i] = false
                trailCount[i] = 0
                if (aliveBalls() == 0) {
                    loseLife()
                    return
                }
            }
        }
        stepDrops(h)
        stepBolts(h)
    }

    private fun collideBricks(i: Int) {
        var x = ballX[i]
        var y = ballY[i]
        val top = gridTop
        val c0 = floor((x - BALL_R - GRID_LEFT) / CELL_W).toInt().coerceAtLeast(0)
        val c1 = floor((x + BALL_R - GRID_LEFT) / CELL_W).toInt().coerceAtMost(GRID_COLS - 1)
        val r0 = floor((y - BALL_R - top) / CELL_H).toInt().coerceAtLeast(0)
        val r1 = floor((y + BALL_R - top) / CELL_H).toInt().coerceAtMost(rows - 1)
        if (c0 > c1 || r0 > r1) return

        val fire = fireActive
        var bounceX = false
        var bounceY = false
        var nx = 0f
        var ny = 0f
        for (r in r0..r1) {
            for (c in c0..c1) {
                val idx = r * GRID_COLS + c
                val hp = brickHp[idx]
                if (hp == 0) continue
                val l = GRID_LEFT + c * CELL_W + BRICK_GAP * 0.5f
                val t = top + r * CELL_H + BRICK_GAP * 0.5f
                val rr = l + CELL_W - BRICK_GAP
                val b = t + CELL_H - BRICK_GAP
                val cx = x.coerceIn(l, rr)
                val cy = y.coerceIn(t, b)
                val dx = x - cx
                val dy = y - cy
                val d2 = dx * dx + dy * dy
                if (d2 > BALL_R * BALL_R) continue

                hitBrick(idx, fire, byBall = true)
                if (fire && hp > 0) continue

                if (d2 < 1e-6f) {
                    // Ball centre is inside the brick, push out the shortest way.
                    val penL = x - l
                    val penR = rr - x
                    val penT = y - t
                    val penB = b - y
                    val m = min(min(penL, penR), min(penT, penB))
                    when (m) {
                        penL -> { bounceX = true; nx = -1f; x = l - BALL_R }
                        penR -> { bounceX = true; nx = 1f; x = rr + BALL_R }
                        penT -> { bounceY = true; ny = -1f; y = t - BALL_R }
                        else -> { bounceY = true; ny = 1f; y = b + BALL_R }
                    }
                } else if (abs(dx) > abs(dy)) {
                    bounceX = true
                    nx = if (dx > 0f) 1f else -1f
                    x = cx + nx * BALL_R
                } else {
                    bounceY = true
                    ny = if (dy > 0f) 1f else -1f
                    y = cy + ny * BALL_R
                }
            }
        }
        // Set the sign instead of flipping it, otherwise two bricks hit in one step cancel out.
        if (bounceX) ballDx[i] = abs(ballDx[i]) * nx
        if (bounceY) ballDy[i] = abs(ballDy[i]) * ny
        if (bounceX || bounceY) {
            ballX[i] = x
            ballY[i] = y
            ensureAngle(i)
        }
    }

    /** Circle-vs-box bounce against a solid rectangle. */
    private fun collideRect(i: Int, l: Float, t: Float, r: Float, b: Float): Boolean {
        val x = ballX[i]
        val y = ballY[i]
        val cx = x.coerceIn(l, r)
        val cy = y.coerceIn(t, b)
        val dx = x - cx
        val dy = y - cy
        val d2 = dx * dx + dy * dy
        if (d2 > BALL_R * BALL_R) return false
        if (d2 < 1e-6f || abs(dy) >= abs(dx)) {
            val below = if (d2 < 1e-6f) y > (t + b) * 0.5f else dy > 0f
            ballDy[i] = if (below) abs(ballDy[i]) else -abs(ballDy[i])
            ballY[i] = if (below) b + BALL_R else t - BALL_R
        } else {
            ballDx[i] = if (dx > 0f) abs(ballDx[i]) else -abs(ballDx[i])
            ballX[i] = if (dx > 0f) r + BALL_R else l - BALL_R
        }
        ensureAngle(i)
        burst(cx, cy, PART_SPARK, 3, 0f, 0f)
        return true
    }

    private fun collideDrones(i: Int) {
        val rr = BALL_R + DRONE_R
        for (k in 0 until MAX_DRONES) {
            if (!droneAlive[k]) continue
            val dx = ballX[i] - droneX[k]
            val dy = ballY[i] - droneY[k]
            val d2 = dx * dx + dy * dy
            if (d2 > rr * rr) continue
            if (!fireActive) {
                val d = sqrt(d2).coerceAtLeast(1e-3f)
                val nx = dx / d
                val ny = dy / d
                val dot = ballDx[i] * nx + ballDy[i] * ny
                if (dot < 0f) {
                    ballDx[i] -= 2f * dot * nx
                    ballDy[i] -= 2f * dot * ny
                }
                ballX[i] = droneX[k] + nx * rr
                ballY[i] = droneY[k] + ny * rr
                ensureAngle(i)
            }
            destroyDrone(k, reward = true)
        }
    }

    private fun collidePaddle(i: Int) {
        if (ballDy[i] <= 0f) return
        val top = paddleTop
        val half = paddleW * 0.5f
        val x = ballX[i]
        val y = ballY[i]
        if (y + BALL_R < top || y - BALL_R > top + PADDLE_H) return
        if (x < paddleX - half - BALL_R || x > paddleX + half + BALL_R) return

        val off = ((x - paddleX) / (half + BALL_R)).coerceIn(-1f, 1f)
        val spin = (paddleVel * SPIN_GAIN).coerceIn(-MAX_SPIN, MAX_SPIN)
        val a = (off * MAX_BOUNCE + spin).coerceIn(-MAX_BOUNCE, MAX_BOUNCE)
        ballDx[i] = sin(a)
        ballDy[i] = -cos(a)
        ballY[i] = top - BALL_R
        ballSpeed = min(ballSpeed * SPEED_GAIN_PER_HIT, baseSpeed * SPEED_CAP)
        combo = 0
        paddleGlow = 0.6f
        burst(x, top, PART_SPARK, 5, 0f, -200f)

        if (isActive(PowerUp.Catch)) {
            ballStuck[i] = true
            ballSwing[i] = false
            ballStuckOffset[i] = (x - paddleX).coerceIn(-half, half)
            serving = true
        }
    }

    private fun stepDrops(h: Float) {
        val top = paddleTop
        val half = paddleW * 0.5f
        for (d in 0 until MAX_DROPS) {
            if (!dropAlive[d]) continue
            dropY[d] += DROP_SPEED * h
            val y = dropY[d]
            if (y + DROP_H * 0.5f >= top && y - DROP_H * 0.5f <= top + PADDLE_H &&
                abs(dropX[d] - paddleX) <= half + DROP_W * 0.5f
            ) {
                dropAlive[d] = false
                collect(powerUps[dropType[d]])
            } else if (y - DROP_H > floorY) {
                dropAlive[d] = false
            }
        }
    }

    private fun stepBolts(h: Float) {
        for (b in 0 until MAX_BOLTS) {
            if (!boltAlive[b]) continue
            boltY[b] -= BOLT_SPEED * h
            val x = boltX[b]
            val y = boltY[b]
            if (y < CEILING) {
                boltAlive[b] = false
                continue
            }
            var blocked = false
            for (k in 0 until moverCount) {
                if (x >= moverX[k] && x <= moverX[k] + moverW[k] && y >= moverY[k] && y <= moverY[k] + MOVER_H) {
                    blocked = true
                    moverFlash[k] = 1f
                }
            }
            if (blocked) {
                boltAlive[b] = false
                burst(x, y, PART_SPARK, 4, 0f, 100f)
                continue
            }
            var hitDrone = false
            for (k in 0 until MAX_DRONES) {
                if (!droneAlive[k]) continue
                if (abs(x - droneX[k]) <= DRONE_R && abs(y - droneY[k]) <= DRONE_R) {
                    destroyDrone(k, reward = true)
                    hitDrone = true
                    break
                }
            }
            if (hitDrone) {
                boltAlive[b] = false
                continue
            }
            if (boss.active && boltHitsBoss(x, y)) {
                boltAlive[b] = false
                continue
            }
            val c = floor((x - GRID_LEFT) / CELL_W).toInt()
            val r = floor((y - gridTop) / CELL_H).toInt()
            if (c !in 0 until GRID_COLS || r !in 0 until rows) continue
            val idx = r * GRID_COLS + c
            if (brickHp[idx] == 0) continue
            boltAlive[b] = false
            burst(x, y, PART_LASER, 5, 0f, 100f)
            hitBrick(idx, fire = false, byBall = false)
            if (phase != BrickPhase.Playing) return
        }
    }

    private fun fireBolts() {
        val half = paddleW * 0.5f - 16f
        spawnBolt(paddleX - half)
        spawnBolt(paddleX + half)
    }

    private fun spawnBolt(x: Float) {
        for (b in 0 until MAX_BOLTS) {
            if (boltAlive[b]) continue
            boltAlive[b] = true
            boltX[b] = x
            boltY[b] = paddleTop - BOLT_LEN
            return
        }
    }

    // Boss

    /** BossHost implementation. */
    private val host = object : BossHost {
        override val paddleX: Float get() = this@BrickEngine.paddleX
        override val paddleTop: Float get() = this@BrickEngine.paddleTop
        override fun random(): Float = rng.nextFloat()

        override fun targetX(): Float {
            val i = lowestBall()
            return if (i >= 0) ballX[i] else this@BrickEngine.paddleX
        }

        override fun targetY(): Float {
            val i = lowestBall()
            return if (i >= 0) ballY[i] else this@BrickEngine.paddleTop
        }

        override fun fireShot(x: Float, y: Float, vx: Float, vy: Float, kind: Int) = spawnShot(x, y, vx, vy, kind)

        override fun spawnMinion(x: Float, y: Float) = spawnDroneAt(x, y)

        override fun growBricks(count: Int, kind: Int) = this@BrickEngine.growBricks(count, kind)

        override fun effect(x: Float, y: Float, color: Int, count: Int, ring: Float) {
            burst(x, y, color, count, 0f, 0f)
            if (ring > 0f) spawnRing(x, y, color, ring)
        }

        override fun shake(amount: Float) {
            shake = max(shake, amount)
        }

        override fun haptic(strong: Boolean) = onHaptic(strong)

        override fun beamStrike(x: Float): Boolean {
            if (abs(this@BrickEngine.paddleX - x) > paddleW * 0.5f + BEAM_HALF_W) return false
            stunPaddle(BEAM_STUN_TIME, PART_PLASMA)
            return true
        }
    }

    private fun lowestBall(): Int {
        var best = -1
        for (i in 0 until MAX_BALLS) {
            if (!ballAlive[i] || ballStuck[i]) continue
            if (best < 0 || ballY[i] > ballY[best]) best = i
        }
        return best
    }

    /** Void Heart gravity, bends the ball towards the core. */
    private fun bend(i: Int, g: Float, h: Float, move: Float) {
        val dx = boss.x - ballX[i]
        val dy = boss.y - ballY[i]
        val d = sqrt(dx * dx + dy * dy)
        if (d >= GRAVITY_RADIUS || d < 1f || move <= 0f) return
        // a * h^2 / step = change in heading for this sub-step
        val f = g * (1f - d / GRAVITY_RADIUS) * h * h / move
        var vx = ballDx[i] + dx / d * f
        var vy = ballDy[i] + dy / d * f
        val len = sqrt(vx * vx + vy * vy).coerceAtLeast(1e-4f)
        vx /= len
        vy /= len
        ballDx[i] = vx
        ballDy[i] = vy
        ensureAngle(i)
    }

    /** Ball vs boss, and the pharaoh's shields. */
    private fun collideBoss(i: Int) {
        val k = boss.kind ?: return
        if (k == BossKind.Pharaoh) {
            for (p in 0 until PLATE_COUNT) {
                if (boss.plateHp[p] <= 0) continue
                val px = boss.plateX(p)
                val py = boss.plateY(p)
                if (collideCircle(i, px, py, PLATE_R)) {
                    if (boss.hitPlate(p)) {
                        burst(px, py, PART_TEAL, 18, 0f, 0f)
                        spawnRing(px, py, PART_GOLD, 90f)
                        addScore(120)
                        onHaptic(true)
                    } else {
                        burst(px, py, PART_GOLD, 6, 0f, 0f)
                        onHaptic(false)
                    }
                }
            }
        }
        if (!boss.solid) return
        val hit = if (k.round) {
            collideCircle(i, boss.x, boss.y, k.halfW)
        } else {
            collideRect(i, boss.x - k.halfW, boss.y - k.halfH, boss.x + k.halfW, boss.y + k.halfH)
        }
        if (!hit) return
        val blast = blastCharges > 0
        val amount = when {
            blast -> 3f
            fireActive -> 2f
            else -> 1f
        }
        // Ball stuck above the boss bounces fast, so slow the damage down.
        val grace = if (ballY[i] < boss.y - k.halfH * 0.8f) 0.45f else 0.1f
        if (hitBoss(amount, ballX[i], ballY[i], fromBall = true, grace = grace) && blast) {
            blastCharges--
            spawnRing(ballX[i], ballY[i], PART_FIRE, 190f)
            burst(ballX[i], ballY[i], PART_FIRE, 30, 0f, 0f)
        }
    }

    /** Circle-vs-circle bounce. */
    private fun collideCircle(i: Int, cx: Float, cy: Float, r: Float): Boolean {
        val rr = BALL_R + r
        val dx = ballX[i] - cx
        val dy = ballY[i] - cy
        val d2 = dx * dx + dy * dy
        if (d2 > rr * rr) return false
        val d = sqrt(d2).coerceAtLeast(1e-3f)
        val nx = dx / d
        val ny = dy / d
        val dot = ballDx[i] * nx + ballDy[i] * ny
        if (dot < 0f) {
            ballDx[i] -= 2f * dot * nx
            ballDy[i] -= 2f * dot * ny
        }
        ballX[i] = cx + nx * rr
        ballY[i] = cy + ny * rr
        ensureAngle(i)
        burst(cx + nx * r, cy + ny * r, PART_SPARK, 3, 0f, 0f)
        return true
    }

    /** Damage plus effects. Returns true if it landed. */
    private fun hitBoss(amount: Float, x: Float, y: Float, fromBall: Boolean, grace: Float = 0.1f): Boolean {
        val k = boss.kind ?: return false
        val result = boss.damage(amount, fromBall, grace)
        if (result == BOSS_IMMUNE) {
            burst(x, y, PART_SPARK, 3, 0f, 0f)
            return false
        }
        bossHealth = boss.healthFraction
        addScore((40 * boss.stage * amount).toInt())
        burst(x, y, k.colorA, 10, 0f, 0f)
        burst(x, y, PART_SPARK, 6, 0f, 0f)
        spawnRing(x, y, k.colorB, 70f)
        shake = max(shake, 0.25f)
        if (fromBall) maybeDrop(x, y, BOSS_DROP_CHANCE)
        when (result) {
            BOSS_ENRAGED -> {
                bossStage = boss.stage
                shake = max(shake, 1.1f)
                spawnRing(boss.x, boss.y, k.colorA, 320f)
                spawnRing(boss.x, boss.y, PART_FIRE, 220f)
                burst(boss.x, boss.y, k.colorA, 40, 0f, 0f)
                onHaptic(true)
            }
            BOSS_KILLED -> bossKilled()
            else -> onHaptic(false)
        }
        return true
    }

    /** Boss died: clear balls, shots and drops. */
    private fun bossKilled() {
        bossHealth = 0f
        shake = 1.4f
        onHaptic(true)
        for (i in 0 until MAX_BALLS) {
            if (!ballAlive[i]) continue
            burst(ballX[i], ballY[i], PART_SPARK, 14, 0f, 0f)
            ballAlive[i] = false
        }
        serving = false
        for (k in 0 until MAX_DRONES) if (droneAlive[k]) destroyDrone(k, reward = false)
        for (s in 0 until MAX_SHOTS) if (shotAlive[s]) killShot(s)
        dropAlive.fill(false)
        boltAlive.fill(false)
    }

    /** Laser vs boss, shields and shots. Returns true if the bolt was used up. */
    private fun boltHitsBoss(x: Float, y: Float): Boolean {
        if (boss.kind == BossKind.Pharaoh) {
            for (p in 0 until PLATE_COUNT) {
                if (boss.plateHp[p] <= 0) continue
                if (abs(x - boss.plateX(p)) <= PLATE_R && abs(y - boss.plateY(p)) <= PLATE_R) {
                    if (boss.hitPlate(p)) burst(boss.plateX(p), boss.plateY(p), PART_TEAL, 14, 0f, 0f)
                    burst(x, y, PART_LASER, 4, 0f, 100f)
                    return true
                }
            }
        }
        if (boss.hitTest(x, y, 0f)) {
            burst(x, y, PART_LASER, 5, 0f, 100f)
            hitBoss(LASER_DAMAGE, x, y, fromBall = false)
            return true
        }
        for (s in 0 until MAX_SHOTS) {
            if (!shotAlive[s] || shotBack[s] || isReflectable(shotKind[s])) continue
            if (abs(x - shotX[s]) <= SHOT_R && abs(y - shotY[s]) <= SHOT_R + BOLT_LEN) {
                killShot(s)
                addScore(25)
                return true
            }
        }
        return false
    }

    private fun spawnShot(x: Float, y: Float, vx: Float, vy: Float, kind: Int) {
        for (s in 0 until MAX_SHOTS) {
            if (shotAlive[s]) continue
            shotAlive[s] = true
            shotBack[s] = false
            shotX[s] = x
            shotY[s] = y
            shotVx[s] = vx
            shotVy[s] = vy
            shotKind[s] = kind
            shotAge[s] = 0f
            return
        }
    }

    private fun killShot(s: Int) {
        shotAlive[s] = false
        val color = when (shotKind[s]) {
            SHOT_SEED -> PART_LEAF
            SHOT_SAND -> PART_SAND
            SHOT_ANKH, SHOT_STAR -> PART_GOLD
            SHOT_PLASMA -> PART_PLASMA
            else -> PART_VOID
        }
        burst(shotX[s], shotY[s], color, 8, 0f, 0f)
    }

    /** Sends a golden shot back at the boss. */
    private fun reflectShot(s: Int) {
        val dx = boss.x - shotX[s]
        val dy = boss.y - shotY[s]
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        shotVx[s] = dx / d * REFLECT_SPEED
        shotVy[s] = dy / d * REFLECT_SPEED
        shotBack[s] = true
        spawnRing(shotX[s], shotY[s], PART_GOLD, 110f)
        burst(shotX[s], shotY[s], PART_GOLD, 12, 0f, -200f)
        addScore(50)
        paddleGlow = 1f
        onHaptic(false)
    }

    private fun stunPaddle(seconds: Float, color: Int) {
        paddleStun = max(paddleStun, seconds)
        shake = max(shake, 0.7f)
        spawnRing(paddleX, paddleTop, color, 150f)
        burst(paddleX, paddleTop, color, 14, 0f, -150f)
        onHaptic(true)
    }

    /** Moves boss projectiles. */
    private fun stepShots(dt: Float) {
        val top = paddleTop
        val half = paddleW * 0.5f
        val reach = (BALL_R + SHOT_R) * (BALL_R + SHOT_R)
        for (s in 0 until MAX_SHOTS) {
            if (!shotAlive[s]) continue
            shotAge[s] += dt
            if (shotKind[s] == SHOT_SEED && !shotBack[s]) shotVy[s] += SEED_FALL * dt
            val x = shotX[s] + shotVx[s] * dt
            val y = shotY[s] + shotVy[s] * dt
            shotX[s] = x
            shotY[s] = y
            if (x < -40f || x > WORLD_W + 40f || y > floorY + 40f || y < -40f) {
                shotAlive[s] = false
                continue
            }
            if (shotBack[s]) {
                if (boss.kind == BossKind.Pharaoh && shotHitsPlate(s)) continue
                if (boss.hitTest(x, y, SHOT_R)) {
                    shotAlive[s] = false
                    spawnRing(x, y, PART_GOLD, 160f)
                    burst(x, y, PART_GOLD, 24, 0f, 0f)
                    hitBoss(REFLECT_DAMAGE, x, y, fromBall = false)
                }
                continue
            }
            val reflectable = isReflectable(shotKind[s])
            if (y + SHOT_R >= top && y - SHOT_R <= top + PADDLE_H && abs(x - paddleX) <= half + SHOT_R) {
                if (reflectable && paddleStun <= 0f) {
                    shotY[s] = top - SHOT_R
                    reflectShot(s)
                } else {
                    killShot(s)
                    if (!reflectable) stunPaddle(SHOT_STUN_TIME, PART_DRONE)
                }
                continue
            }
            for (i in 0 until MAX_BALLS) {
                if (!ballAlive[i] || ballStuck[i]) continue
                val dx = ballX[i] - x
                val dy = ballY[i] - y
                if (dx * dx + dy * dy > reach) continue
                if (reflectable) {
                    reflectShot(s)
                } else {
                    killShot(s)
                    addScore(25)
                }
                break
            }
        }
    }

    private fun shotHitsPlate(s: Int): Boolean {
        for (p in 0 until PLATE_COUNT) {
            if (boss.plateHp[p] <= 0) continue
            val dx = shotX[s] - boss.plateX(p)
            val dy = shotY[s] - boss.plateY(p)
            val rr = SHOT_R + PLATE_R
            if (dx * dx + dy * dy > rr * rr) continue
            shotAlive[s] = false
            // Reflected ankh breaks the shield in one go.
            while (boss.plateHp[p] > 0) boss.hitPlate(p)
            burst(boss.plateX(p), boss.plateY(p), PART_TEAL, 20, 0f, 0f)
            spawnRing(boss.plateX(p), boss.plateY(p), PART_GOLD, 120f)
            addScore(150)
            return true
        }
        return false
    }

    /** Grow bricks in random empty cells, never on top of a ball. */
    private fun growBricks(count: Int, kind: Int) {
        val first = level.growFrom.coerceIn(0, rows - 1)
        val span = rows - first
        if (span <= 0) return
        var placed = 0
        var tries = 0
        while (placed < count && tries < 80) {
            tries++
            val idx = (first + rng.nextInt(span)) * GRID_COLS + rng.nextInt(GRID_COLS)
            if (brickHp[idx] != 0) continue
            val cx = brickCenterX(idx)
            val cy = brickCenterY(idx)
            var blocked = false
            for (i in 0 until MAX_BALLS) {
                if (ballAlive[i] && abs(ballX[i] - cx) < CELL_W && abs(ballY[i] - cy) < CELL_H * 1.5f) blocked = true
            }
            if (blocked) continue
            val hp = BrickKind.hitPoints(kind)
            brickKind[idx] = kind
            brickHp[idx] = hp
            brickMaxHp[idx] = hp
            brickFlash[idx] = 1f
            if (hp > 0) breakableLeft++
            spawnRing(cx, cy, kind, 50f)
            burst(cx, cy, kind, 6, 0f, 0f)
            placed++
        }
    }

    /** Drone spawned by a boss, ignores the level's drone limit. */
    private fun spawnDroneAt(x: Float, y: Float) {
        var alive = 0
        var slot = -1
        for (k in 0 until MAX_DRONES) {
            if (droneAlive[k]) alive++ else if (slot < 0) slot = k
        }
        if (alive >= MAX_MINIONS || slot < 0) return
        val cx = x.coerceIn(WALL + 140f, WORLD_W - WALL - 140f)
        droneAlive[slot] = true
        droneBaseX[slot] = cx
        droneX[slot] = cx
        droneY[slot] = y
        droneAge[slot] = 0f
        spawnRing(cx, y, PART_DRONE, 70f)
    }

    // Drones

    private fun stepDrones(dt: Float, live: Boolean) {
        if (live && level.maxDrones > 0) {
            droneClock -= dt
            if (droneClock <= 0f) {
                spawnDrone()
                droneClock = level.droneEvery * (0.75f + rng.nextFloat() * 0.5f)
            }
        }
        val top = paddleTop
        val half = paddleW * 0.5f
        for (k in 0 until MAX_DRONES) {
            if (!droneAlive[k]) continue
            droneAge[k] += dt
            droneY[k] += DRONE_SPEED * dt
            droneX[k] = (droneBaseX[k] + sin(droneAge[k] * 1.4f) * DRONE_SWAY).coerceIn(WALL + DRONE_R, WORLD_W - WALL - DRONE_R)
            val y = droneY[k]
            // Drone hits the paddle -> stun
            if (y + DRONE_R >= top && y - DRONE_R <= top + PADDLE_H && abs(droneX[k] - paddleX) <= half + DRONE_R) {
                destroyDrone(k, reward = false)
                paddleStun = STUN_TIME
                shake = max(shake, 0.7f)
                spawnRing(paddleX, top, PART_DRONE, 150f)
                onHaptic(true)
            } else if (y - DRONE_R > floorY) {
                droneAlive[k] = false
            }
        }
    }

    private fun spawnDrone() {
        var alive = 0
        var slot = -1
        for (k in 0 until MAX_DRONES) {
            if (droneAlive[k]) alive++ else if (slot < 0) slot = k
        }
        if (alive >= level.maxDrones || slot < 0) return
        val x = WALL + 140f + rng.nextFloat() * (WORLD_W - 2 * WALL - 280f)
        droneAlive[slot] = true
        droneBaseX[slot] = x
        droneX[slot] = x
        droneY[slot] = gridTop + rows * CELL_H + 30f
        droneAge[slot] = 0f
        spawnRing(x, droneY[slot], PART_DRONE, 70f)
    }

    private fun destroyDrone(k: Int, reward: Boolean) {
        droneAlive[k] = false
        val x = droneX[k]
        val y = droneY[k]
        burst(x, y, PART_DRONE, 16, 0f, 0f)
        burst(x, y, PART_SPARK, 8, 0f, 0f)
        spawnRing(x, y, PART_DRONE, 90f)
        if (reward) {
            addScore(DRONE_POINTS)
            maybeDrop(x, y, DRONE_DROP_CHANCE)
        }
    }

    // Bricks

    private fun hitBrick(index: Int, fire: Boolean, byBall: Boolean) {
        val hp = brickHp[index]
        if (hp == 0) return
        brickFlash[index] = 1f
        if (hp < 0) {
            burst(brickCenterX(index), brickCenterY(index), PART_SPARK, 4, 0f, 0f)
            return
        }
        val blast = byBall && blastCharges > 0
        val next = if (fire || blast) 0 else hp - 1
        if (next == 0) {
            val kind = brickKind[index]
            destroyBrick(index)
            if (blast) {
                blastCharges--
                if (kind != BrickKind.TNT) explode(index, brickCenterX(index), brickCenterY(index))
            }
        } else {
            brickHp[index] = next
            addScore(5)
            burst(brickCenterX(index), brickCenterY(index), brickKind[index], 6, 0f, 0f)
        }
    }

    private fun destroyBrick(index: Int) {
        val kind = brickKind[index]
        brickHp[index] = 0
        breakableLeft--
        combo += 1
        addScore(BrickKind.points(kind) * min(combo, 8))
        val cx = brickCenterX(index)
        val cy = brickCenterY(index)
        burst(cx, cy, kind, 16, 0f, 0f)
        spawnRing(cx, cy, kind, 58f)
        maybeDrop(cx, cy, DROP_CHANCE)
        if (kind == BrickKind.TNT) explode(index, cx, cy)
        if (breakableLeft <= 0 && phase == BrickPhase.Playing && level.boss == null) levelCleared()
    }

    private fun explode(index: Int, cx: Float, cy: Float) {
        shake = max(shake, 1f)
        onHaptic(true)
        spawnRing(cx, cy, PART_FIRE, 190f)
        burst(cx, cy, PART_FIRE, 34, 0f, 0f)
        val r0 = index / GRID_COLS
        val c0 = index % GRID_COLS
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = r0 + dr
                val c = c0 + dc
                if (r !in 0 until rows || c !in 0 until GRID_COLS) continue
                val j = r * GRID_COLS + c
                if (brickHp[j] > 0) {
                    brickFlash[j] = 1f
                    destroyBrick(j)
                }
            }
        }
    }

    private fun maybeDrop(x: Float, y: Float, chance: Float) {
        if (phase != BrickPhase.Playing || rng.nextFloat() > chance) return
        var slot = -1
        for (d in 0 until MAX_DROPS) if (!dropAlive[d]) { slot = d; break }
        if (slot < 0) return
        val curse = rng.nextFloat() < level.curseChance
        var roll = rng.nextInt(if (curse) curseWeight else goodWeight)
        var type = 0
        for (k in powerUps.indices) {
            val p = powerUps[k]
            if (p.curse != curse) continue
            roll -= p.weight
            if (roll < 0) { type = k; break }
        }
        if (type == PowerUp.Life.ordinal && lives >= MAX_LIVES) type = PowerUp.Multi.ordinal
        dropAlive[slot] = true
        dropX[slot] = x
        dropY[slot] = y
        dropType[slot] = type
    }

    private fun collect(p: PowerUp) {
        when (p) {
            PowerUp.Expand -> { timers[PowerUp.Shrink.ordinal] = 0f; timers[p.ordinal] = p.duration }
            PowerUp.Shrink -> { timers[PowerUp.Expand.ordinal] = 0f; timers[p.ordinal] = p.duration }
            PowerUp.Slow -> { timers[PowerUp.Rush.ordinal] = 0f; timers[p.ordinal] = p.duration }
            PowerUp.Rush -> { timers[PowerUp.Slow.ordinal] = 0f; timers[p.ordinal] = p.duration }
            PowerUp.Laser, PowerUp.Fire, PowerUp.Catch, PowerUp.Double, PowerUp.Reverse ->
                timers[p.ordinal] = p.duration
            PowerUp.Multi -> splitBalls()
            PowerUp.Shield -> shieldCharges = min(shieldCharges + 1, 3)
            PowerUp.Life -> lives = min(lives + 1, MAX_LIVES)
            PowerUp.Blast -> blastCharges = min(blastCharges + BLAST_CHARGES, BLAST_CHARGES * 2)
        }
        if (p == PowerUp.Laser) laserClock = 0f
        if (p.curse) {
            spawnRing(paddleX, paddleTop, PART_CURSE, 130f)
            burst(paddleX, paddleTop, PART_CURSE, 14, 0f, -250f)
            shake = max(shake, 0.4f)
            onHaptic(true)
        } else {
            addScore(50)
            paddleGlow = 1f
            spawnRing(paddleX, paddleTop, PART_GOLD, 130f)
            burst(paddleX, paddleTop, PART_GOLD, 14, 0f, -250f)
            onHaptic(false)
        }
        lastPickup = p
        pickupSerial += 1
    }

    private fun splitBalls() {
        launchStuck()
        for (i in 0 until MAX_BALLS) splitMask[i] = ballAlive[i]
        for (i in 0 until MAX_BALLS) {
            if (!splitMask[i]) continue
            spawnSplit(i, SPLIT_ANGLE)
            spawnSplit(i, -SPLIT_ANGLE)
        }
    }

    private fun spawnSplit(src: Int, angle: Float) {
        for (j in 0 until MAX_BALLS) {
            if (ballAlive[j]) continue
            val c = cos(angle)
            val s = sin(angle)
            ballAlive[j] = true
            ballStuck[j] = false
            ballSwing[j] = false
            ballX[j] = ballX[src]
            ballY[j] = ballY[src]
            ballDx[j] = ballDx[src] * c - ballDy[src] * s
            ballDy[j] = ballDx[src] * s + ballDy[src] * c
            trailCount[j] = 0
            ensureAngle(j)
            return
        }
    }

    // Helpers

    private fun addScore(points: Int) {
        score += if (isActive(PowerUp.Double)) points * 2 else points
    }

    private fun ensureAngle(i: Int) {
        if (abs(ballDy[i]) >= MIN_VERTICAL) return
        val sy = if (ballDy[i] < 0f) -1f else 1f
        val sx = if (ballDx[i] < 0f) -1f else 1f
        ballDy[i] = sy * MIN_VERTICAL
        ballDx[i] = sx * sqrt(1f - MIN_VERTICAL * MIN_VERTICAL)
    }

    private fun aliveBalls(): Int {
        var n = 0
        for (i in 0 until MAX_BALLS) if (ballAlive[i]) n++
        return n
    }

    private fun brickCenterX(i: Int): Float = GRID_LEFT + (i % GRID_COLS + 0.5f) * CELL_W
    private fun brickCenterY(i: Int): Float = gridTop + (i / GRID_COLS + 0.5f) * CELL_H

    private fun recordTrails() {
        val fire = fireActive
        for (i in 0 until MAX_BALLS) {
            if (!ballAlive[i]) {
                trailCount[i] = 0
                continue
            }
            val head = (trailHead[i] + 1) % TRAIL_LEN
            trailHead[i] = head
            trailX[i * TRAIL_LEN + head] = ballX[i]
            trailY[i * TRAIL_LEN + head] = ballY[i]
            trailCount[i] = if (ballStuck[i]) 1 else min(trailCount[i] + 1, TRAIL_LEN)
            if (fire && !ballStuck[i]) {
                emit(
                    x = ballX[i] + (rng.nextFloat() - 0.5f) * BALL_R,
                    y = ballY[i] + (rng.nextFloat() - 0.5f) * BALL_R,
                    vx = (rng.nextFloat() - 0.5f) * 90f,
                    vy = -140f - rng.nextFloat() * 80f,
                    life = 0.3f + rng.nextFloat() * 0.2f,
                    size = 5f + rng.nextFloat() * 5f,
                    color = PART_FIRE,
                )
            }
        }
    }

    private fun emit(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Int) {
        val k = partCursor
        partCursor = (partCursor + 1) % MAX_PARTICLES
        partX[k] = x
        partY[k] = y
        partVx[k] = vx
        partVy[k] = vy
        partLife[k] = life
        partMax[k] = life
        partSize[k] = size
        partColor[k] = color
    }

    private fun burst(x: Float, y: Float, color: Int, count: Int, biasX: Float, biasY: Float) {
        repeat(count) {
            val a = rng.nextFloat() * TWO_PI
            val speed = 120f + rng.nextFloat() * 420f
            emit(
                x = x + (rng.nextFloat() - 0.5f) * CELL_W * 0.7f,
                y = y + (rng.nextFloat() - 0.5f) * CELL_H * 0.6f,
                vx = cos(a) * speed + biasX,
                vy = sin(a) * speed - 140f + biasY,
                life = 0.4f + rng.nextFloat() * 0.5f,
                size = 4f + rng.nextFloat() * 7f,
                color = color,
            )
        }
    }

    private fun spawnRing(x: Float, y: Float, color: Int, radius: Float) {
        val k = ringCursor
        ringCursor = (ringCursor + 1) % MAX_RINGS
        ringX[k] = x
        ringY[k] = y
        ringLife[k] = RING_LIFE
        ringRadius[k] = radius
        ringColor[k] = color
    }

    private fun updateEffects(dt: Float) {
        val drag = 1f - 1.6f * dt
        for (k in 0 until MAX_PARTICLES) {
            if (partLife[k] <= 0f) continue
            partLife[k] -= dt
            partVy[k] += GRAVITY * dt
            partVx[k] *= drag
            partX[k] += partVx[k] * dt
            partY[k] += partVy[k] * dt
        }
        for (k in 0 until MAX_RINGS) if (ringLife[k] > 0f) ringLife[k] -= dt
        val count = rows * GRID_COLS
        for (k in 0 until count) if (brickFlash[k] > 0f) brickFlash[k] = max(0f, brickFlash[k] - dt * 6f)
        for (k in 0 until moverCount) if (moverFlash[k] > 0f) moverFlash[k] = max(0f, moverFlash[k] - dt * 5f)
        paddleGlow = max(0f, paddleGlow - dt * 2.5f)
        if (shake > 0f) {
            shake = max(0f, shake - dt * 3.2f)
            val amp = shake * shake * 10f
            shakeX = (rng.nextFloat() * 2f - 1f) * amp
            shakeY = (rng.nextFloat() * 2f - 1f) * amp
        } else {
            shakeX = 0f
            shakeY = 0f
        }
    }
}
