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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Boss projectiles
const val MAX_SHOTS = 64
const val SHOT_R = 13f

/** Treant acorn: falls and speeds up under gravity. */
const val SHOT_SEED = 0

/** Pharaoh sand orb. */
const val SHOT_SAND = 1

/** Pharaoh golden ankh. */
const val SHOT_ANKH = 2

/** Dreadnought plasma bolt. */
const val SHOT_PLASMA = 3

/** Void Heart dark matter. */
const val SHOT_VOID = 4

/** Void Heart star shard. */
const val SHOT_STAR = 5
const val SHOT_KIND_COUNT = 6

/** Golden shots can be hit back at the boss. */
fun isReflectable(kind: Int): Boolean = kind == SHOT_ANKH || kind == SHOT_STAR

// Boss geometry and pacing
const val PLATE_COUNT = 3
const val PLATE_R = 30f
const val PLATE_ORBIT = 175f
const val PLATE_HP = 3
const val BEAM_HALF_W = 34f
const val BOSS_DEATH_TIME = 2.6f
private const val ENTER_TIME = 1.8f
private const val TWO_PI_F = (PI * 2.0).toFloat()

/** Results of [BrickBoss.damage]. */
const val BOSS_IMMUNE = 0
const val BOSS_HIT = 1
const val BOSS_ENRAGED = 2
const val BOSS_KILLED = 3

/** The four bosses, in order. */
enum class BossKind(
    val title: String,
    val epithet: String,
    val maxHp: Float,
    val halfW: Float,
    val halfH: Float,
    val round: Boolean,
    val homeY: Float,
    val interval: Float,
    val colorA: Int,
    val colorB: Int,
) {
    Treant("ELDER TREANT", "THE FOREST WAKES", 26f, 150f, 118f, false, 214f, 2.7f, PART_LEAF, PART_GOLD),
    Pharaoh("SCARAB PHARAOH", "RISEN FROM THE SANDS", 32f, 112f, 108f, false, 230f, 2.7f, PART_SAND, PART_TEAL),
    Dreadnought("DREADNOUGHT X9", "THE FLEET HAS ARRIVED", 52f, 205f, 66f, false, 184f, 2.6f, PART_PLASMA, PART_LASER),
    VoidHeart("THE VOID HEART", "ALL LIGHT ENDS HERE", 80f, 100f, 100f, true, 250f, 2.1f, PART_VOID, PART_CURSE),
}

/** What the boss needs from the engine. */
internal interface BossHost {
    val paddleX: Float
    val paddleTop: Float
    fun random(): Float

    /** X of the ball closest to the paddle, or the paddle when none is in flight. */
    fun targetX(): Float
    fun targetY(): Float
    fun fireShot(x: Float, y: Float, vx: Float, vy: Float, kind: Int)
    fun spawnMinion(x: Float, y: Float)
    fun growBricks(count: Int, kind: Int)
    fun effect(x: Float, y: Float, color: Int, count: Int, ring: Float)
    fun shake(amount: Float)
    fun haptic(strong: Boolean)

    /** Beam at [x] is firing. Returns true if it hit the paddle. */
    fun beamStrike(x: Float): Boolean
}

/** Boss state and AI. */
class BrickBoss {
    var kind: BossKind? = null
        private set
    val active: Boolean get() = kind != null

    var x = WORLD_W / 2f
        private set
    var y = -400f
        private set
    var hp = 0f
        private set
    var maxHp = 1f
        private set
    var stage = 1
        private set

    /** 1 just after a hit, decaying to 0. */
    var flash = 0f
        private set

    /** 1 just after a stage change, decaying to 0. */
    var rage = 0f
        private set

    /** Seconds since the fight began. */
    var age = 0f
        private set

    /** Entrance progress 0..1. */
    var enter = 0f
        private set

    /** Death animation time left. */
    var dying = 0f
        private set
    var defeated = false
        private set

    /** Sway in degrees, for the treant and the dreadnought's banking. */
    var tilt = 0f
        private set

    /** Direction to the tracked ball, used for the pupils. */
    var lookX = 0f
        private set
    var lookY = 1f
        private set

    /** Eyelid openness, 0 closed to 1 open. */
    var blink = 1f
        private set

    /** Goes 0 to 1 just before an attack. */
    var mouth = 0f
        private set

    /** Visibility; the Void Heart fades out to teleport. */
    var alpha = 1f
        private set

    /** Dreadnought core hatch, 0 shut to 1 open. */
    var hatch = 0f
        private set

    // Pharaoh shields
    var plateAngle = 0f
        private set
    val plateHp = IntArray(PLATE_COUNT)
    val plateFlash = FloatArray(PLATE_COUNT)
    private val plateTimer = FloatArray(PLATE_COUNT)

    // Dreadnought beams
    val beamX = FloatArray(2)
    var beamCount = 0
        private set
    var beamWarn = 0f
        private set
    var beamFire = 0f
        private set
    private var beamStruck = false
    private var hatchHold = 0f

    private var homeX = WORLD_W / 2f
    private var homeY = 0f
    private var moveClock = 0f
    private var attackClock = 0f
    private var step = 0
    private var iframes = 0f
    private var warpState = 0
    private var warpClock = 0f
    private var spiralLeft = 0f
    private var spiralClock = 0f
    private var spiralAngle = 0f
    private var deathClock = 0f

    /** Health 0..1, for the HUD bar. */
    val healthFraction: Float get() = if (kind == null) 0f else (hp / maxHp).coerceIn(0f, 1f)

    /** True while the body blocks balls and takes damage. */
    val solid: Boolean get() = kind != null && hp > 0f && enter >= 1f && alpha > 0.35f

    /** Gravity pull in units/s². Only the Void Heart has one. */
    val gravity: Float
        get() = if (kind == BossKind.VoidHeart && hp > 0f && alpha > 0.5f) 1500f * (1f + 0.3f * (stage - 1)) else 0f

    /** Starts a fight with [k], or clears the boss when `null`. */
    fun reset(k: BossKind?) {
        kind = k
        if (k == null) return
        hp = k.maxHp
        maxHp = k.maxHp
        stage = 1
        flash = 0f
        rage = 0f
        age = 0f
        enter = 0f
        dying = 0f
        defeated = false
        tilt = 0f
        mouth = 0f
        alpha = 1f
        hatch = 0f
        hatchHold = 0f
        homeX = WORLD_W / 2f
        homeY = k.homeY
        x = homeX
        y = -k.halfH - 300f
        moveClock = 0f
        attackClock = k.interval + 0.8f
        step = 0
        iframes = 0f
        warpState = 0
        spiralLeft = 0f
        beamCount = 0
        beamWarn = 0f
        beamFire = 0f
        plateAngle = 0f
        for (p in 0 until PLATE_COUNT) {
            plateHp[p] = if (k == BossKind.Pharaoh) PLATE_HP else 0
            plateTimer[p] = 0f
            plateFlash[p] = 0f
        }
    }

    /** Centre of scarab plate [p]. */
    fun plateX(p: Int): Float = x + cos(plateAngle + p * TWO_PI_F / PLATE_COUNT) * PLATE_ORBIT

    fun plateY(p: Int): Float = y + sin(plateAngle + p * TWO_PI_F / PLATE_COUNT) * PLATE_ORBIT * 0.62f

    /** True if plate [p] is on the front half of its orbit. */
    fun plateInFront(p: Int): Boolean = sin(plateAngle + p * TWO_PI_F / PLATE_COUNT) > 0f

    /** True if a circle of radius [r] at ([px], [py]) touches the body. */
    fun hitTest(px: Float, py: Float, r: Float): Boolean {
        val k = kind ?: return false
        if (!solid) return false
        return if (k.round) {
            val dx = px - x
            val dy = py - y
            val rr = k.halfW + r
            dx * dx + dy * dy <= rr * rr
        } else {
            abs(px - x) <= k.halfW + r && abs(py - y) <= k.halfH + r
        }
    }

    /** Short pause in attacks after the player loses a life. */
    fun calm() {
        attackClock = max(attackClock, 2.2f)
        beamWarn = 0f
        beamFire = 0f
        spiralLeft = 0f
    }

    /** Returns one of the BOSS_* results. */
    fun damage(amount: Float, fromBall: Boolean, grace: Float = 0.1f): Int {
        val k = kind ?: return BOSS_IMMUNE
        if (!solid || alpha < 0.6f || (fromBall && iframes > 0f)) return BOSS_IMMUNE
        val mult = if (k == BossKind.Dreadnought && hatch > 0.5f) 2f else 1f
        hp -= amount * mult
        flash = 1f
        if (fromBall) iframes = grace
        if (hp <= 0f) {
            hp = 0f
            dying = BOSS_DEATH_TIME
            deathClock = 0f
            beamWarn = 0f
            beamFire = 0f
            spiralLeft = 0f
            return BOSS_KILLED
        }
        val next = when {
            hp > maxHp * 0.66f -> 1
            hp > maxHp * 0.33f -> 2
            else -> 3
        }
        if (next != stage) {
            stage = next
            rage = 1f
            attackClock = min(attackClock, 1.2f)
            return BOSS_ENRAGED
        }
        return BOSS_HIT
    }

    /** Returns true if the plate broke. */
    fun hitPlate(p: Int): Boolean {
        if (plateHp[p] <= 0) return false
        plateHp[p]--
        plateFlash[p] = 1f
        if (plateHp[p] == 0) {
            plateTimer[p] = 9f - stage * 1.5f
            return true
        }
        return false
    }

    /** Advances movement, timers and the attack pattern. */
    internal fun update(dt: Float, host: BossHost, live: Boolean) {
        val k = kind ?: return
        age += dt
        flash = max(0f, flash - dt * 5f)
        rage = max(0f, rage - dt * 0.8f)
        iframes = max(0f, iframes - dt)
        for (p in 0 until PLATE_COUNT) plateFlash[p] = max(0f, plateFlash[p] - dt * 5f)
        if (enter < 1f) enter = min(1f, enter + dt / ENTER_TIME)

        if (dying > 0f) {
            dieStep(dt, host, k)
            return
        }
        if (defeated) return

        val beamBusy = beamWarn > 0f || beamFire > 0f
        if (!beamBusy && spiralLeft <= 0f) moveClock += dt
        move(k)
        val eased = easeOutBack(enter)
        y -= (1f - eased) * (homeY + k.halfH + 300f)

        val tx = host.targetX() - x
        val ty = host.targetY() - y
        val d = sqrt(tx * tx + ty * ty).coerceAtLeast(1f)
        lookX += (tx / d - lookX) * min(1f, dt * 8f)
        lookY += (ty / d - lookY) * min(1f, dt * 8f)
        val cycle = (age + k.ordinal * 0.9f) % 3.6f
        blink = if (cycle < 0.16f) abs(cycle / 0.08f - 1f) else 1f

        updateBeams(dt, host)
        updateWarp(dt)
        updatePlates(dt, host, k)
        updateSpiral(dt, host)

        if (!live || enter < 1f) {
            mouth = max(0f, mouth - dt * 3f)
            return
        }
        attackClock -= dt
        mouth = (1f - attackClock / 0.45f).coerceIn(0f, 1f)
        if (attackClock <= 0f) {
            attack(k, host)
            step++
            val pace = when (stage) {
                1 -> 1f
                2 -> 0.8f
                else -> 0.62f
            }
            attackClock += k.interval * pace + beamWarn + beamFire
        }
    }

    private fun move(k: BossKind) {
        val t = moveClock
        when (k) {
            BossKind.Treant -> {
                x = homeX + sin(t * 0.5f) * (40f + 30f * stage)
                y = homeY + sin(t * 1.3f) * 6f
                tilt = sin(t * 1.1f) * 2.5f * stage
            }
            BossKind.Pharaoh -> {
                x = homeX + sin(t * (0.5f + 0.12f * stage)) * 250f
                y = homeY + sin(t * 2f) * 10f
                tilt = cos(t * (0.5f + 0.12f * stage)) * 4f
            }
            BossKind.Dreadnought -> {
                val w = 0.42f * (1f + 0.2f * (stage - 1))
                x = homeX + sin(t * w) * 230f
                y = homeY + sin(t * 0.9f) * 18f
                tilt = cos(t * w) * 3.5f
            }
            BossKind.VoidHeart -> {
                x = (homeX + sin(t * 0.4f) * 90f).coerceIn(140f, WORLD_W - 140f)
                y = homeY + sin(t * 0.7f) * 30f
                tilt = 0f
            }
        }
    }

    private fun attack(k: BossKind, host: BossHost) {
        when (k) {
            BossKind.Treant -> when (step % 3) {
                0 -> acornRain(host)
                1 -> if (stage >= 2) regrow(host) else acornRain(host)
                else -> if (stage >= 2) summon(host, stage - 1, 120f, 70f) else acornRain(host)
            }
            BossKind.Pharaoh -> when (step % 3) {
                0 -> sandFan(host)
                1 -> ankh(host)
                else -> if (stage >= 2) summon(host, stage - 1, 90f, 90f) else sandFan(host)
            }
            BossKind.Dreadnought -> when (step % 3) {
                0 -> plasmaVolley(host)
                1 -> chargeBeams(host)
                else -> summon(host, stage, 80f, 60f)
            }
            BossKind.VoidHeart -> when (step % 4) {
                0 -> nova(host)
                1 -> starLance(host)
                2 -> if (stage >= 2) warp(host) else nova(host)
                else -> if (stage == 3) spiral() else starLance(host)
            }
        }
    }

    // Moves

    /** Acorns shaken from the canopy, aimed around the paddle. */
    private fun acornRain(host: BossHost) {
        val n = 1 + stage
        val speed = 240f + 50f * stage
        for (i in 0 until n) {
            val sx = x + (i - (n - 1) * 0.5f) * 70f
            val sy = y - 20f
            aimShot(host, sx, sy, host.paddleX + (host.random() - 0.5f) * 180f, host.paddleTop, speed, SHOT_SEED)
        }
        host.effect(x, y - 60f, PART_LEAF, 14, 0f)
    }

    /** Roots push new bricks up below the treant. */
    private fun regrow(host: BossHost) {
        host.growBricks(2 + stage * 2, if (stage == 3) BrickKind.SILVER else BrickKind.GREEN)
        host.effect(x, y + 110f, PART_LEAF, 20, 160f)
        host.shake(0.35f)
    }

    /** Launches [count] minions from either side of the body. */
    private fun summon(host: BossHost, count: Int, spread: Float, below: Float) {
        for (i in 0 until count) {
            val side = if (i % 2 == 0) -1f else 1f
            host.spawnMinion(x + side * spread * (1 + i / 2), y + below)
        }
    }

    /** A fan of sand orbs centred on the paddle. */
    private fun sandFan(host: BossHost) {
        val n = 1 + 2 * stage
        val spread = 0.5f + 0.15f * stage
        val base = atan2(host.paddleTop - y, host.paddleX - x)
        val speed = 300f + 40f * stage
        for (i in 0 until n) {
            val a = base + (i - (n - 1) * 0.5f) * spread / (n - 1).coerceAtLeast(1)
            host.fireShot(x, y + 60f, cos(a) * speed, sin(a) * speed, SHOT_SAND)
        }
        host.effect(x, y + 60f, PART_SAND, 12, 90f)
    }

    /** Golden ankh, can be hit back. */
    private fun ankh(host: BossHost) {
        aimShot(host, x, y + 70f, host.paddleX, host.paddleTop, 300f, SHOT_ANKH)
        if (stage >= 2) {
            val base = atan2(host.paddleTop - y, host.paddleX - x)
            for (side in -1..1 step 2) {
                val a = base + side * 0.35f
                host.fireShot(x, y + 60f, cos(a) * 340f, sin(a) * 340f, SHOT_SAND)
            }
        }
        host.effect(x, y + 70f, PART_GOLD, 12, 90f)
    }

    /** Plasma bolts from the wing cannons, leading the paddle. */
    private fun plasmaVolley(host: BossHost) {
        val n = 2 + stage
        val speed = 380f + 50f * stage
        for (i in 0 until n) {
            val side = if (i % 2 == 0) -1f else 1f
            val sx = x + side * 150f
            val sy = y + 40f
            val lead = (i - (n - 1) * 0.5f) * 60f
            aimShot(host, sx, sy, host.paddleX + lead, host.paddleTop, speed, SHOT_PLASMA)
        }
        host.effect(x - 150f, y + 40f, PART_PLASMA, 6, 50f)
        host.effect(x + 150f, y + 40f, PART_PLASMA, 6, 50f)
    }

    /** Marks one or two beam columns, the first on the paddle; they fire after the warning. */
    private fun chargeBeams(host: BossHost) {
        beamCount = if (stage >= 2) 2 else 1
        beamX[0] = host.paddleX.coerceIn(WALL + BEAM_HALF_W, WORLD_W - WALL - BEAM_HALF_W)
        if (beamCount == 2) {
            var other = WALL + BEAM_HALF_W + host.random() * (WORLD_W - 2f * (WALL + BEAM_HALF_W))
            if (abs(other - beamX[0]) < 260f) other = if (beamX[0] < WORLD_W / 2f) beamX[0] + 300f else beamX[0] - 300f
            beamX[1] = other.coerceIn(WALL + BEAM_HALF_W, WORLD_W - WALL - BEAM_HALF_W)
        }
        beamWarn = 1.1f - 0.15f * (stage - 1)
        beamFire = 0f
        host.haptic(false)
    }

    /** A ring of dark matter with star shards that can be reflected. */
    private fun nova(host: BossHost) {
        val n = 6 + 2 * stage
        val speed = 220f + 40f * stage
        val offset = host.random() * 0.4f
        for (i in 0 until n) {
            val a = offset + i * TWO_PI_F / n
            // The shot pointing straight down is a star, later stages add two more.
            val off = a - PI.toFloat() / 2f
            val diff = abs(atan2(sin(off), cos(off)))
            val gap = TWO_PI_F / n
            val star = diff <= gap * 0.5f || (stage >= 2 && abs(diff - gap * 2f) <= gap * 0.5f)
            host.fireShot(x, y, cos(a) * speed, sin(a) * speed, if (star) SHOT_STAR else SHOT_VOID)
        }
        host.effect(x, y, PART_VOID, 20, 220f)
        host.shake(0.3f)
    }

    /** One star shard at the paddle, flanked by two dark bolts. */
    private fun starLance(host: BossHost) {
        aimShot(host, x, y + 80f, host.paddleX, host.paddleTop, 330f, SHOT_STAR)
        val base = atan2(host.paddleTop - y, host.paddleX - x)
        for (side in -1..1 step 2) {
            val a = base + side * 0.3f
            host.fireShot(x, y + 60f, cos(a) * 360f, sin(a) * 360f, SHOT_VOID)
        }
    }

    /** Teleport and grow some crystals. */
    private fun warp(host: BossHost) {
        warpState = 1
        warpClock = 0.45f
        host.growBricks(3, BrickKind.GOLD)
        host.effect(x, y, PART_VOID, 26, 200f)
    }

    private fun spiral() {
        spiralLeft = 2.4f
        spiralClock = 0f
    }

    private fun aimShot(host: BossHost, sx: Float, sy: Float, tx: Float, ty: Float, speed: Float, kind: Int) {
        val dx = tx - sx
        val dy = ty - sy
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        host.fireShot(sx, sy, dx / d * speed, dy / d * speed, kind)
    }

    // Continuous behaviour

    private fun updateBeams(dt: Float, host: BossHost) {
        if (beamWarn > 0f) {
            beamWarn -= dt
            if (beamWarn <= 0f) {
                beamWarn = 0f
                beamFire = 0.85f
                beamStruck = false
                host.shake(0.5f)
                host.haptic(true)
            }
        } else if (beamFire > 0f) {
            beamFire = max(0f, beamFire - dt)
            if (!beamStruck) {
                for (b in 0 until beamCount) if (host.beamStrike(beamX[b])) beamStruck = true
            }
            if (beamFire == 0f) hatchHold = 1.4f
        }
        if (hatchHold > 0f) hatchHold -= dt
        val open = beamWarn > 0f || beamFire > 0f || hatchHold > 0f
        hatch += ((if (open) 1f else 0f) - hatch) * min(1f, dt * 6f)
    }

    private fun updateWarp(dt: Float) {
        when (warpState) {
            1 -> {
                warpClock -= dt
                alpha = (warpClock / 0.45f).coerceIn(0f, 1f)
                if (warpClock <= 0f) {
                    homeX = 260f + ((age * 7919f) % 1f) * 480f
                    homeY = 210f + ((age * 104729f) % 1f) * 80f
                    warpState = 2
                    warpClock = 0.45f
                }
            }
            2 -> {
                warpClock -= dt
                alpha = 1f - (warpClock / 0.45f).coerceIn(0f, 1f)
                if (warpClock <= 0f) {
                    alpha = 1f
                    warpState = 0
                }
            }
        }
    }

    private fun updatePlates(dt: Float, host: BossHost, k: BossKind) {
        if (k != BossKind.Pharaoh) return
        plateAngle += dt * (1.1f + 0.35f * stage)
        for (p in 0 until PLATE_COUNT) {
            if (plateHp[p] > 0) continue
            plateTimer[p] -= dt
            if (plateTimer[p] <= 0f) {
                plateHp[p] = PLATE_HP
                plateFlash[p] = 1f
                host.effect(plateX(p), plateY(p), PART_TEAL, 10, 60f)
            }
        }
    }

    private fun updateSpiral(dt: Float, host: BossHost) {
        if (spiralLeft <= 0f) return
        spiralLeft -= dt
        spiralClock -= dt
        if (spiralClock > 0f) return
        spiralClock = 0.1f
        spiralAngle += 0.5f
        for (arm in 0..1) {
            val a = spiralAngle + arm * PI.toFloat()
            // Only fire downwards.
            if (sin(a) < -0.2f) continue
            host.fireShot(x + cos(a) * 90f, y + sin(a) * 90f, cos(a) * 260f, sin(a) * 260f, SHOT_VOID)
        }
    }

    private fun dieStep(dt: Float, host: BossHost, k: BossKind) {
        dying -= dt
        deathClock -= dt
        flash = max(flash, 0.5f + 0.5f * sin(age * 40f))
        tilt = sin(age * 30f) * 4f
        if (deathClock <= 0f) {
            deathClock = 0.11f
            val ex = x + (host.random() - 0.5f) * k.halfW * 1.8f
            val ey = y + (host.random() - 0.5f) * k.halfH * 1.8f
            host.effect(ex, ey, if (host.random() < 0.5f) k.colorA else PART_FIRE, 16, 120f)
            host.shake(0.6f)
        }
        if (dying <= 0f) {
            dying = 0f
            defeated = true
            alpha = 0f
            host.effect(x, y, k.colorA, 60, 420f)
            host.effect(x, y, PART_GOLD, 40, 300f)
            host.shake(1.6f)
            host.haptic(true)
        }
    }
}

/** easeOutBack, for the entrance. */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}
