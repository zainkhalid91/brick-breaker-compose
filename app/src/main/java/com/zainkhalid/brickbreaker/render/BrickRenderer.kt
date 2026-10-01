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

package com.zainkhalid.brickbreaker.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zainkhalid.brickbreaker.engine.BALL_R
import com.zainkhalid.brickbreaker.engine.BEAM_HALF_W
import com.zainkhalid.brickbreaker.engine.BOLT_LEN
import com.zainkhalid.brickbreaker.engine.BRICK_GAP
import com.zainkhalid.brickbreaker.engine.BossKind
import com.zainkhalid.brickbreaker.engine.BrickEngine
import com.zainkhalid.brickbreaker.engine.BrickTheme
import com.zainkhalid.brickbreaker.engine.CELL_H
import com.zainkhalid.brickbreaker.engine.CELL_W
import com.zainkhalid.brickbreaker.engine.DRONE_R
import com.zainkhalid.brickbreaker.engine.DROP_H
import com.zainkhalid.brickbreaker.engine.DROP_W
import com.zainkhalid.brickbreaker.engine.GRID_COLS
import com.zainkhalid.brickbreaker.engine.GRID_LEFT
import com.zainkhalid.brickbreaker.engine.MAX_DRONES
import com.zainkhalid.brickbreaker.engine.MOVER_H
import com.zainkhalid.brickbreaker.engine.MAX_BALLS
import com.zainkhalid.brickbreaker.engine.MAX_BOLTS
import com.zainkhalid.brickbreaker.engine.MAX_DROPS
import com.zainkhalid.brickbreaker.engine.MAX_PARTICLES
import com.zainkhalid.brickbreaker.engine.MAX_RINGS
import com.zainkhalid.brickbreaker.engine.MAX_SHOTS
import com.zainkhalid.brickbreaker.engine.PLATE_COUNT
import com.zainkhalid.brickbreaker.engine.PLATE_HP
import com.zainkhalid.brickbreaker.engine.PADDLE_H
import com.zainkhalid.brickbreaker.engine.PowerUp
import com.zainkhalid.brickbreaker.engine.RING_LIFE
import com.zainkhalid.brickbreaker.engine.SHOT_R
import com.zainkhalid.brickbreaker.engine.SHOT_SEED
import com.zainkhalid.brickbreaker.engine.isReflectable
import com.zainkhalid.brickbreaker.engine.TRAIL_LEN
import com.zainkhalid.brickbreaker.engine.WALL
import com.zainkhalid.brickbreaker.engine.WORLD_W
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val FireTrail = Color(0xFFFF8A2A)
private val LaserRed = Color(0xFFFF3B5C)
private val CatchGreen = Color(0xFF34D873)
private val ReverseViolet = Color(0xFFB44BFF)
private val BlastLime = Color(0xFFB6FF2E)
private val BeamPink = Color(0xFFFF2E6E)
private val BeamRose = Color(0xFFFF8AB8)
private val GoldGlow = Color(0xFFFFD23A)
private val Pollen = Color(0xFFFFE9A0)
private val Leaf = Color(0xFFE8792A)
private val Firefly = Color(0xFFCFFF6A)
private val Snow = Color(0xFFF2FBFF)
private val Mote = Color(0xFFC8FF9A)
private val SandStreak = Color(0xFFFFE0A0)
private val StarDust = Color(0xFFD8E8FF)
private val VoidDust = Color(0xFFD9A2FF)
private val ReticleStroke = Stroke(width = 3f)
private val TIMED_POWER_UPS: Array<PowerUp> = PowerUp.entries.filter { it.duration > 0f }.toTypedArray()

/** Draws one frame of the world. */
fun DrawScope.drawBrickWorld(e: BrickEngine, sp: BrickSprites, bs: BossSprites?) {
    val s = e.pxPerUnit
    drawAmbient(sp.theme, e.time, s)
    translate(e.shakeX * s, e.shakeY * s) {
        drawShield(e, sp, s)
        if (bs != null) drawBoss(e, bs, s)
        drawBricks(e, sp, s)
        drawMovers(e, sp, s)
        drawRings(e, sp, s)
        drawDrones(e, sp, s)
        drawDrops(e, sp, s)
        if (bs != null) {
            drawBeams(e, bs, s)
            drawShots(e, bs, s)
        }
        drawBolts(e, s)
        drawPaddle(e, sp, s)
        drawBalls(e, sp, s)
        drawParticles(e, s)
    }
    drawTimers(e, sp, s)
}

private fun DrawScope.drawBricks(e: BrickEngine, sp: BrickSprites, s: Float) {
    val pad = sp.brickPad.toFloat()
    val bw = sp.brickW.toFloat()
    val bh = sp.brickH.toFloat()
    val intro = e.introProgress
    val count = e.rows * GRID_COLS
    for (i in 0 until count) {
        val hp = e.brickHp[i]
        if (hp == 0) continue
        val col = i % GRID_COLS
        val row = i / GRID_COLS
        var alpha = 1f
        var drop = 0f
        if (intro < 1f) {
            // Rows drop in from the top
            val t = (intro * 1.8f - row * 0.07f - col * 0.012f).coerceIn(0f, 1f)
            val inv = 1f - t
            val eased = 1f - inv * inv * inv
            alpha = eased
            drop = -(1f - eased) * (e.gridTop + (row + 2) * CELL_H) * s
        }
        val x = ((GRID_LEFT + col * CELL_W + BRICK_GAP * 0.5f) * s).roundToInt().toFloat()
        val y = ((e.gridTop + row * CELL_H + BRICK_GAP * 0.5f) * s).roundToInt().toFloat() + drop
        val flash = e.brickFlash[i]
        val sprite = sp.bricks[e.brickKind[i]]
        if (flash > 0f) {
            val k = 1f + flash * 0.09f
            scale(k, k, Offset(x + bw * 0.5f, y + bh * 0.5f)) {
                drawImage(sprite, Offset(x - pad, y - pad))
                drawRect(Color.White, Offset(x, y), Size(bw, bh), alpha = flash * 0.7f)
            }
        } else {
            drawImage(sprite, Offset(x - pad, y - pad), alpha = alpha)
        }
        val damage = e.brickMaxHp[i] - hp
        if (hp > 0 && damage > 0) drawImage(sp.cracks[min(damage, 2) - 1], Offset(x, y), alpha = alpha)
    }
}

private fun DrawScope.drawShield(e: BrickEngine, sp: BrickSprites, s: Float) {
    if (e.shieldCharges <= 0) return
    val pulse = 0.65f + 0.35f * sin(e.time * 6f)
    for (k in 0 until e.shieldCharges) {
        val y = (e.shieldY + k * 7f) * s
        val start = Offset(WALL * s, y)
        val end = Offset((WORLD_W - WALL) * s, y)
        drawLine(sp.accent, start, end, strokeWidth = 14f * s, alpha = 0.18f * pulse, blendMode = BlendMode.Plus)
        drawLine(sp.accent, start, end, strokeWidth = 6f * s, alpha = 0.35f * pulse, blendMode = BlendMode.Plus)
        drawLine(Color.White, start, end, strokeWidth = 2f * s, alpha = 0.9f)
    }
}

private fun DrawScope.drawRings(e: BrickEngine, sp: BrickSprites, s: Float) {
    for (k in 0 until MAX_RINGS) {
        val life = e.ringLife[k]
        if (life <= 0f) continue
        val t = 1f - life / RING_LIFE
        val inv = 1f - t
        val radius = e.ringRadius[k] * (1f - inv * inv) * s
        drawCircle(
            color = BrickPalette.particle[e.ringColor[k]],
            radius = radius,
            center = Offset(e.ringX[k] * s, e.ringY[k] * s),
            alpha = (1f - t) * 0.85f,
            style = sp.ringStroke,
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawDrops(e: BrickEngine, sp: BrickSprites, s: Float) {
    for (d in 0 until MAX_DROPS) {
        if (!e.dropAlive[d]) continue
        val img = sp.drops[e.dropType[d]]
        val bob = sin(e.time * 7f + d) * 2.5f * s
        val cx = e.dropX[d] * s
        val cy = e.dropY[d] * s + bob
        drawImage(img, Offset(cx - img.width * 0.5f, cy - img.height * 0.5f))
    }
}

private fun DrawScope.drawBolts(e: BrickEngine, s: Float) {
    for (b in 0 until MAX_BOLTS) {
        if (!e.boltAlive[b]) continue
        val x = e.boltX[b] * s
        val top = Offset(x, e.boltY[b] * s)
        val bottom = Offset(x, (e.boltY[b] + BOLT_LEN) * s)
        drawLine(LaserRed, top, bottom, strokeWidth = 10f * s, alpha = 0.35f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        drawLine(Color.White, top, bottom, strokeWidth = 3f * s, cap = StrokeCap.Round)
    }
}

/** Three-slice paddle: caps drawn 1:1, a 2 px body column stretched between them. */
private fun DrawScope.drawPaddle(e: BrickEngine, sp: BrickSprites, s: Float) {
    val img = sp.paddle
    val pp = sp.paddlePad
    val capPx = sp.paddleCap
    val capInner = capPx - pp
    val x0 = ((e.paddleX - e.paddleW * 0.5f) * s).roundToInt()
    val widthPx = (e.paddleW * s).roundToInt()
    val top = (e.paddleTop * s).roundToInt() - pp
    val h = img.height
    val bodyStart = x0 + capInner
    val bodyW = (widthPx - capInner * 2).coerceAtLeast(0)

    // Blink while stunned
    val alpha = if (e.paddleStun > 0f && ((e.time * 18f).toInt() and 1) == 0) 0.35f else 1f
    drawImage(img, IntOffset.Zero, IntSize(capPx, h), IntOffset(x0 - pp, top), IntSize(capPx, h), alpha = alpha)
    drawImage(img, IntOffset(img.width / 2 - 1, 0), IntSize(2, h), IntOffset(bodyStart, top), IntSize(bodyW, h), alpha = alpha)
    drawImage(img, IntOffset(img.width - capPx, 0), IntSize(capPx, h), IntOffset(bodyStart + bodyW, top), IntSize(capPx, h), alpha = alpha)

    val py = e.paddleTop * s
    val ph = PADDLE_H * s
    if (e.isActive(PowerUp.Laser)) {
        val cw = 10f * s
        for (side in 0..1) {
            val cx = if (side == 0) x0 + 16f * s else x0 + widthPx - 16f * s
            drawRoundRect(Color(0xFF2A2E36), Offset(cx - cw * 0.5f, py - 14f * s), Size(cw, 16f * s), CornerRadius(3f * s))
            drawCircle(LaserRed, 5f * s, Offset(cx, py - 14f * s), blendMode = BlendMode.Plus)
        }
    }
    if (e.isActive(PowerUp.Catch)) {
        val pulse = 0.55f + 0.45f * sin(e.time * 9f)
        drawLine(
            CatchGreen, Offset(x0 + capInner * 0.6f, py), Offset(x0 + widthPx - capInner * 0.6f, py),
            strokeWidth = 5f * s, alpha = pulse, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
        )
    }
    if (e.isActive(PowerUp.Reverse)) {
        val pulse = 0.35f + 0.25f * sin(e.time * 12f)
        drawRoundRect(
            ReverseViolet, Offset(x0.toFloat(), py), Size(widthPx.toFloat(), ph), CornerRadius(ph * 0.5f),
            alpha = pulse, blendMode = BlendMode.Plus,
        )
    }
    if (e.paddleGlow > 0f) {
        drawRoundRect(
            Color.White, Offset(x0.toFloat(), py), Size(widthPx.toFloat(), ph), CornerRadius(ph * 0.5f),
            alpha = e.paddleGlow * 0.55f, blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawBalls(e: BrickEngine, sp: BrickSprites, s: Float) {
    val fire = e.fireActive
    val img = if (fire) sp.fireBall else sp.ball
    val half = img.width * 0.5f
    val trail = if (fire) FireTrail else sp.accent
    val r = BALL_R * s
    for (i in 0 until MAX_BALLS) {
        if (!e.ballAlive[i]) continue
        val n = e.trailCount[i]
        val head = e.trailHead[i]
        val base = i * TRAIL_LEN
        for (k in 1 until n) {
            val idx = (head - k + TRAIL_LEN) % TRAIL_LEN
            val f = 1f - k.toFloat() / TRAIL_LEN
            drawCircle(
                trail, r * (0.25f + 0.7f * f), Offset(e.trailX[base + idx] * s, e.trailY[base + idx] * s),
                alpha = 0.5f * f * f, blendMode = BlendMode.Plus,
            )
        }
        val bx = e.ballX[i] * s
        val by = e.ballY[i] * s
        if (e.ballStuck[i]) drawAimGuide(e, i, bx, by, s, sp.accent)
        drawImage(img, Offset(bx - half, by - half))
        if (e.blastCharges > 0) {
            drawCircle(BlastLime, r * (1.7f + 0.2f * sin(e.time * 14f)), Offset(bx, by), alpha = 0.8f, style = sp.ringStroke, blendMode = BlendMode.Plus)
        }
    }
}

/** Dotted launch guide, fading with distance, for a ball resting on the paddle. */
private fun DrawScope.drawAimGuide(e: BrickEngine, i: Int, bx: Float, by: Float, s: Float, color: Color) {
    if (!e.serving) return
    val a = e.aimAngle(i)
    val dx = sin(a)
    val dy = -cos(a)
    val march = (e.time * 60f) % 26f
    for (k in 1..9) {
        val d = (k * 26f + march) * s
        val f = 1f - k / 10f
        drawCircle(color, 3.4f * s * f + 1f, Offset(bx + dx * d, by + dy * d), alpha = 0.85f * f, blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawParticles(e: BrickEngine, s: Float) {
    for (k in 0 until MAX_PARTICLES) {
        val life = e.partLife[k]
        if (life <= 0f) continue
        val f = life / e.partMax[k]
        val size = e.partSize[k] * s * (0.35f + 0.65f * f)
        drawRect(
            BrickPalette.particle[e.partColor[k]],
            Offset(e.partX[k] * s - size * 0.5f, e.partY[k] * s - size * 0.5f),
            Size(size, size),
            alpha = f,
            blendMode = BlendMode.Plus,
        )
    }
}

/** Active power-up timers, under the paddle. */
private fun DrawScope.drawTimers(e: BrickEngine, sp: BrickSprites, s: Float) {
    val k = 0.62f
    val w = DROP_W * k
    val h = DROP_H * k
    val y = e.floorY - 70f
    var x = WALL + 14f
    for (p in TIMED_POWER_UPS) {
        if (!e.isActive(p)) continue
        drawMiniCapsule(sp, p, x, y, k, s)
        val frac = e.timerFraction(p)
        drawRect(Color.White.copy(alpha = 0.18f), Offset(x * s, (y + h + 5f) * s), Size(w * s, 4f * s))
        drawRect(BrickPalette.powerUp(p), Offset(x * s, (y + h + 5f) * s), Size(w * frac * s, 4f * s))
        x += w + 12f
    }
    if (e.blastCharges > 0) {
        drawMiniCapsule(sp, PowerUp.Blast, x, y, k, s)
        for (c in 0 until e.blastCharges) {
            drawCircle(BlastLime, 3f * s, Offset((x + 6f + c * 10f) * s, (y + h + 7f) * s))
        }
    }
}

private fun DrawScope.drawMiniCapsule(sp: BrickSprites, p: PowerUp, x: Float, y: Float, k: Float, s: Float) {
    val img = sp.drops[p.ordinal]
    val padX = (img.width - DROP_W * s) * 0.5f * k
    val padY = (img.height - DROP_H * s) * 0.5f * k
    drawImage(
        img, IntOffset.Zero, IntSize(img.width, img.height),
        IntOffset((x * s - padX).roundToInt(), (y * s - padY).roundToInt()),
        IntSize((img.width * k).roundToInt(), (img.height * k).roundToInt()),
    )
}

/** Moving steel bars. */
private fun DrawScope.drawMovers(e: BrickEngine, sp: BrickSprites, s: Float) {
    val h = MOVER_H * s
    val corner = CornerRadius(h * 0.5f)
    for (k in 0 until e.moverCount) {
        val x = e.moverX[k] * s
        val y = e.moverY[k] * s
        val w = e.moverW[k] * s
        val flash = e.moverFlash[k]
        drawRoundRect(sp.accent2, Offset(x - 8f * s, y - 8f * s), Size(w + 16f * s, h + 16f * s), corner, alpha = 0.1f + 0.2f * flash)
        drawRoundRect(sp.accent2, Offset(x - 3f * s, y - 3f * s), Size(w + 6f * s, h + 6f * s), corner, alpha = 0.25f + 0.3f * flash)
        translate(x, y) {
            drawRoundRect(sp.moverBrush, Offset.Zero, Size(w, h), corner)
        }
        drawCircle(Color.White, h * 0.22f, Offset(x + h * 0.5f, y + h * 0.5f), alpha = 0.6f + 0.4f * flash)
        drawCircle(Color.White, h * 0.22f, Offset(x + w - h * 0.5f, y + h * 0.5f), alpha = 0.6f + 0.4f * flash)
    }
}

/** Drones. */
private fun DrawScope.drawDrones(e: BrickEngine, sp: BrickSprites, s: Float) {
    val img = sp.drone
    val half = img.width * 0.5f
    for (k in 0 until MAX_DRONES) {
        if (!e.droneAlive[k]) continue
        val cx = e.droneX[k] * s
        val cy = e.droneY[k] * s
        val appear = (e.droneAge[k] * 2.5f).coerceAtMost(1f)
        rotate(e.droneAge[k] * 140f, Offset(cx, cy)) {
            drawImage(img, Offset(cx - half, cy - half), alpha = appear)
        }
        drawCircle(BrickPalette.DroneRed, DRONE_R * s * 1.6f, Offset(cx, cy), alpha = 0.08f, blendMode = BlendMode.Plus)
    }
}

// Boss

/** Boss: back layer, body, eyes, core, then hit flash. */
private fun DrawScope.drawBoss(e: BrickEngine, bs: BossSprites, s: Float) {
    val b = e.boss
    val k = b.kind ?: return
    if (b.defeated) return
    val cx = b.x * s
    val cy = b.y * s
    val t = b.age
    var alpha = b.alpha
    if (b.dying in 0.001f..0.5f) alpha *= b.dying / 0.5f
    val pulse = 0.5f + 0.5f * sin(t * 4f)

    when (k) {
        BossKind.VoidHeart -> {
            val sw = 0.85f + 0.15f * sin(t * 1.3f)
            scale(sw, sw, Offset(cx, cy)) {
                drawCentered(bs.extra, cx, cy, alpha = alpha * 0.9f, degrees = t * 38f)
            }
            scale(0.7f, 0.7f, Offset(cx, cy)) {
                drawCentered(bs.extra, cx, cy, alpha = alpha * 0.55f, degrees = -t * 60f)
            }
            // Gravity ripples
            for (r in 0 until 3) {
                val f = 1f - ((t * 0.6f + r / 3f) % 1f)
                drawCircle(VoidDust, (110f + f * 260f) * s, Offset(cx, cy), alpha = (1f - f) * 0.25f * alpha, style = Stroke(width = 2f * s), blendMode = BlendMode.Plus)
            }
        }
        BossKind.Pharaoh -> drawPlates(e, bs, s, front = false)
        BossKind.Dreadnought -> {
            for (side in -1..1 step 2) {
                val flicker = 0.7f + 0.3f * sin(t * 37f + side)
                drawCircle(Color(0xFF4FC3FF), 26f * s * flicker, Offset(cx + side * 100f * s, cy + 64f * s), alpha = 0.35f * alpha, blendMode = BlendMode.Plus)
                drawCircle(Color.White, 10f * s * flicker, Offset(cx + side * 100f * s, cy + 62f * s), alpha = 0.7f * alpha, blendMode = BlendMode.Plus)
            }
        }
        else -> Unit
    }

    rotate(b.tilt, Offset(cx, cy + k.halfH * s)) {
        val left = cx - bs.body.width * 0.5f
        val top = cy - bs.body.height * 0.5f
        drawImage(bs.body, Offset(left, top), alpha = alpha)

        // Core
        when (k) {
            BossKind.Treant -> {
                val m = b.mouth
                drawOval(Color(0xFFFF9A2E), Offset(cx - 30f * s, cy + (62f - 4f - 10f * m) * s), Size(60f * s, (8f + 20f * m) * s), alpha = (0.25f + 0.75f * m) * alpha, blendMode = BlendMode.Plus)
                val k2 = 0.6f + 0.12f * pulse + 0.3f * b.rage
                scale(k2, k2, Offset(cx, cy - 66f * s)) { drawCentered(bs.core, cx, cy - 66f * s, alpha = alpha) }
            }
            BossKind.Pharaoh -> {
                val k2 = 0.35f + 0.1f * pulse + 0.25f * b.mouth
                scale(k2, k2, Offset(cx, cy - 106f * s)) { drawCentered(bs.core, cx, cy - 106f * s, alpha = alpha) }
            }
            BossKind.Dreadnought -> {
                if (b.hatch > 0.02f) {
                    val k2 = 0.3f + 0.7f * b.hatch * (0.9f + 0.1f * pulse)
                    scale(k2, k2 * 0.6f, Offset(cx, cy + 52f * s)) { drawCentered(bs.core, cx, cy + 52f * s, alpha = alpha * b.hatch) }
                }
                // Running lights
                for (n in 0 until 10) {
                    val on = ((t * 8f).toInt() + n) % 5 == 0
                    drawCircle(if (on) Color.White else BeamPink, 3.5f * s, Offset(cx + (-180f + n * 40f) * s, cy + 2f * s), alpha = (if (on) 1f else 0.4f) * alpha, blendMode = BlendMode.Plus)
                }
            }
            BossKind.VoidHeart -> Unit
        }

        drawBossEyes(e, bs, s, cx, cy, alpha)

        if (b.flash > 0f) drawImage(bs.body, Offset(left, top), alpha = b.flash * 0.7f * alpha, colorFilter = bs.flashFilter)
        val rage = if (b.stage == 3) 0.12f + 0.1f * pulse else 0f
        if (rage + b.rage > 0.01f) {
            drawImage(bs.body, Offset(left, top), alpha = min(1f, rage + b.rage * 0.6f) * alpha, colorFilter = bs.rageFilter, blendMode = BlendMode.Plus)
        }
    }
    if (k == BossKind.Pharaoh) drawPlates(e, bs, s, front = true)
}

/** Eyes follow the ball. The Void Heart gets a slit pupil. */
private fun DrawScope.drawBossEyes(e: BrickEngine, bs: BossSprites, s: Float, cx: Float, cy: Float, alpha: Float) {
    val b = e.boss
    val k = b.kind ?: return
    val eyes = bossEyes(k)
    val img = if (b.stage == 3) bs.eyeRage else bs.eye
    var i = 0
    while (i < eyes.size) {
        val ex = cx + eyes[i] * s
        val ey = cy + eyes[i + 1] * s
        when (k) {
            BossKind.Dreadnought -> {
                // Cannons charge up before firing
                val g = 0.25f + 0.75f * b.mouth
                scale(g, g, Offset(ex, ey)) { drawCentered(img, ex, ey, alpha = alpha) }
            }
            BossKind.VoidHeart -> {
                val lid = b.blink.coerceAtLeast(0.05f)
                scale(1f, lid, Offset(ex, ey)) {
                    drawCentered(img, ex, ey, alpha = alpha)
                    val px = ex + b.lookX * 16f * s
                    val py = ey + b.lookY * 16f * s
                    val pw = (12f - 7f * b.mouth) * s
                    drawRoundRect(Color.Black, Offset(px - pw * 0.5f, py - 36f * s), Size(pw, 72f * s), CornerRadius(pw * 0.5f), alpha = alpha)
                }
            }
            else -> {
                val lid = b.blink.coerceAtLeast(0.08f)
                scale(1f, lid, Offset(ex, ey)) {
                    drawCentered(img, ex, ey, alpha = alpha)
                    val reach = if (k == BossKind.Treant) 7f else 6f
                    drawCircle(Color(0xFF120804), 5.5f * s, Offset(ex + b.lookX * reach * s, ey + b.lookY * reach * s), alpha = alpha)
                }
                if (b.mouth > 0.01f) drawCentered(img, ex, ey, alpha = alpha * b.mouth * 0.6f)
            }
        }
        i += 2
    }
}

/** Scarab shields. Back ones go behind the body, front ones on top. */
private fun DrawScope.drawPlates(e: BrickEngine, bs: BossSprites, s: Float, front: Boolean) {
    val b = e.boss
    for (p in 0 until PLATE_COUNT) {
        val hp = b.plateHp[p]
        if (hp <= 0 || b.plateInFront(p) != front) continue
        val angle = b.plateAngle + p * (2f * PI.toFloat() / PLATE_COUNT)
        val depth = 0.82f + 0.18f * sin(angle)
        val px = b.plateX(p) * s
        val py = b.plateY(p) * s
        val a = (0.55f + 0.45f * hp / PLATE_HP) * b.alpha
        scale(depth, depth, Offset(px, py)) {
            drawCentered(bs.extra, px, py, alpha = a, degrees = outwardDegrees(angle))
            if (b.plateFlash[p] > 0f) drawCentered(bs.extra, px, py, alpha = b.plateFlash[p], degrees = outwardDegrees(angle), colorFilter = bs.flashFilter)
        }
    }
}

/** Dreadnought beams. */
private fun DrawScope.drawBeams(e: BrickEngine, bs: BossSprites, s: Float) {
    val b = e.boss
    if (b.beamWarn <= 0f && b.beamFire <= 0f) return
    val top = (b.y + 60f) * s
    val bottom = e.floorY * s
    for (k in 0 until b.beamCount) {
        val x = b.beamX[k] * s
        if (b.beamWarn > 0f) {
            val flicker = 0.35f + 0.35f * sin(e.time * 40f)
            drawLine(BeamPink, Offset(x, top), Offset(x, bottom), strokeWidth = 2f * s, alpha = flicker, blendMode = BlendMode.Plus)
            val ry = e.paddleTop * s
            drawCircle(BeamPink, (BEAM_HALF_W + 10f) * s, Offset(x, ry), alpha = 0.4f + flicker, style = ReticleStroke)
            drawLine(BeamPink, Offset(x - 50f * s, ry), Offset(x + 50f * s, ry), strokeWidth = 2f * s, alpha = flicker)
            val grow = 0.4f + 0.6f * (1f - b.beamWarn)
            scale(grow, grow, Offset(x, top)) { drawCentered(bs.core, x, top) }
        } else {
            val f = (b.beamFire / 0.85f).coerceIn(0f, 1f)
            val w = BEAM_HALF_W * 2f * s * (0.6f + 0.4f * f)
            drawRect(BeamPink, Offset(x - w * 0.65f, top), Size(w * 1.3f, bottom - top), alpha = 0.15f * f, blendMode = BlendMode.Plus)
            drawRect(BeamRose, Offset(x - w * 0.35f, top), Size(w * 0.7f, bottom - top), alpha = 0.3f * f + 0.1f, blendMode = BlendMode.Plus)
            drawRect(Color.White, Offset(x - w * 0.12f, top), Size(w * 0.24f, bottom - top), alpha = f)
            drawCentered(bs.core, x, top, alpha = f)
        }
    }
}

/** Boss shots. Golden ones spin and get a halo. */
private fun DrawScope.drawShots(e: BrickEngine, bs: BossSprites, s: Float) {
    for (n in 0 until MAX_SHOTS) {
        if (!e.shotAlive[n]) continue
        val kind = e.shotKind[n]
        val x = e.shotX[n] * s
        val y = e.shotY[n] * s
        val img = bs.shots[kind]
        if (isReflectable(kind)) {
            val halo = 1.5f + 0.25f * sin(e.shotAge[n] * 12f)
            drawCircle(GoldGlow, SHOT_R * halo * s, Offset(x, y), alpha = if (e.shotBack[n]) 0.6f else 0.3f, blendMode = BlendMode.Plus)
            drawCentered(img, x, y, degrees = e.shotAge[n] * 240f)
        } else {
            drawCentered(img, x, y, degrees = if (kind == SHOT_SEED) e.shotAge[n] * 200f else 0f)
        }
    }
}

// Ambient weather

private const val AMBIENT_COUNT = 42
private val AmbientRnd = kotlin.random.Random(77)
private val AmbX = FloatArray(AMBIENT_COUNT) { AmbientRnd.nextFloat() }
private val AmbY = FloatArray(AMBIENT_COUNT) { AmbientRnd.nextFloat() }
private val AmbSpeed = FloatArray(AMBIENT_COUNT) { 0.5f + AmbientRnd.nextFloat() }
private val AmbPhase = FloatArray(AMBIENT_COUNT) { AmbientRnd.nextFloat() * 6.28f }

/** Weather per world. Worked out from time, so there's no particle state. */
private fun DrawScope.drawAmbient(theme: BrickTheme, time: Float, s: Float) {
    val w = size.width
    val h = size.height
    when (theme) {
        BrickTheme.Canyon -> {
            val cycle = time % 7f
            if (cycle < 0.9f) {
                val f = cycle / 0.9f
                val sx = w * (0.9f - f * 0.7f)
                val sy = h * (0.05f + f * 0.25f)
                for (k in 0 until 10) {
                    drawCircle(Color.White, (3f - k * 0.25f) * s, Offset(sx + k * 9f * s, sy - k * 3.2f * s), alpha = (1f - k / 10f) * (1f - f), blendMode = BlendMode.Plus)
                }
            }
            return
        }
        else -> Unit
    }
    for (k in 0 until AMBIENT_COUNT) {
        val sp = AmbSpeed[k]
        val ph = AmbPhase[k]
        when (theme) {
            BrickTheme.ForestDawn, BrickTheme.Jungle -> {
                val y = h * (1f - ((AmbY[k] + time * 0.015f * sp) % 1f))
                val x = w * AmbX[k] + sin(time * 0.8f + ph) * 20f * s
                val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin(time * 2f + ph))
                drawCircle(if (theme == BrickTheme.Jungle) Mote else Pollen, 2.4f * s * sp, Offset(x, y), alpha = 0.55f * tw, blendMode = BlendMode.Plus)
            }
            BrickTheme.ForestDusk -> {
                val y = h * ((AmbY[k] + time * 0.04f * sp) % 1f)
                val x = (w * AmbX[k] + time * 25f * s * sp + sin(time + ph) * 30f * s) % w
                rotate(time * 90f * sp + ph * 57f, Offset(x, y)) {
                    drawOval(Leaf, Offset(x - 6f * s, y - 2.5f * s), Size(12f * s, 5f * s), alpha = 0.7f)
                }
            }
            BrickTheme.ForestNight -> {
                val x = w * AmbX[k] + sin(time * 0.5f * sp + ph) * 40f * s
                val y = h * (0.15f + AmbY[k] * 0.8f) + cos(time * 0.7f * sp + ph) * 30f * s
                val glow = (0.5f + 0.5f * sin(time * 2.4f * sp + ph)).let { it * it }
                drawCircle(Firefly, 9f * s, Offset(x, y), alpha = 0.18f * glow, blendMode = BlendMode.Plus)
                drawCircle(Firefly, 2.6f * s, Offset(x, y), alpha = 0.9f * glow, blendMode = BlendMode.Plus)
            }
            BrickTheme.Glacier -> {
                val y = h * ((AmbY[k] + time * 0.05f * sp) % 1f)
                val x = w * AmbX[k] + sin(time * 1.2f * sp + ph) * 18f * s
                drawCircle(Snow, (1.6f + 2f * sp) * s, Offset(x, y), alpha = 0.75f)
            }
            BrickTheme.Dunes -> {
                val x = w * ((AmbX[k] + time * 0.12f * sp) % 1f)
                val y = h * (0.55f + AmbY[k] * 0.45f)
                drawLine(SandStreak, Offset(x, y), Offset(x + 26f * s * sp, y + 2f * s), strokeWidth = 1.6f * s, alpha = 0.25f)
            }
            BrickTheme.Orbit, BrickTheme.Nebula -> {
                val y = h * ((AmbY[k] + time * 0.02f * sp) % 1f)
                drawCircle(StarDust, 1.2f * s * sp, Offset(w * AmbX[k], y), alpha = 0.6f, blendMode = BlendMode.Plus)
            }
            BrickTheme.Void -> {
                val f = (AmbY[k] + time * 0.08f * sp) % 1f
                val r = (1f - f) * w * 0.6f + 60f * s
                val a = ph + f * 7f
                drawCircle(VoidDust, 1.8f * s, Offset(w * 0.5f + cos(a) * r, 250f * s + sin(a) * r * 0.5f), alpha = f * 0.8f, blendMode = BlendMode.Plus)
            }
            BrickTheme.Canyon -> Unit
        }
    }
}
