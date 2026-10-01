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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import com.zainkhalid.brickbreaker.engine.BossKind
import com.zainkhalid.brickbreaker.engine.PLATE_R
import com.zainkhalid.brickbreaker.engine.SHOT_ANKH
import com.zainkhalid.brickbreaker.engine.SHOT_KIND_COUNT
import com.zainkhalid.brickbreaker.engine.SHOT_PLASMA
import com.zainkhalid.brickbreaker.engine.SHOT_R
import com.zainkhalid.brickbreaker.engine.SHOT_SAND
import com.zainkhalid.brickbreaker.engine.SHOT_SEED
import com.zainkhalid.brickbreaker.engine.SHOT_STAR
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Baked bitmaps for one boss. Eyes and core are separate so they can animate. */
class BossSprites(
    val kind: BossKind,
    val body: ImageBitmap,
    val eye: ImageBitmap,
    val eyeRage: ImageBitmap,
    val core: ImageBitmap,
    val extra: ImageBitmap,
    val shots: Array<ImageBitmap>,
) {
    val flashFilter: ColorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn)
    val rageFilter: ColorFilter = ColorFilter.tint(Color(0xFFFF2B3A), BlendMode.SrcIn)
}

/** Eye positions relative to the boss centre, in world units (x, y pairs). */
fun bossEyes(kind: BossKind): FloatArray = when (kind) {
    BossKind.Treant -> TreantEyes
    BossKind.Pharaoh -> PharaohEyes
    BossKind.Dreadnought -> DreadnoughtEyes
    BossKind.VoidHeart -> VoidEyes
}

private val TreantEyes = floatArrayOf(-36f, 16f, 36f, 16f)
private val PharaohEyes = floatArrayOf(-34f, -8f, 34f, -8f)
private val DreadnoughtEyes = floatArrayOf(-150f, 50f, 150f, 50f)
private val VoidEyes = floatArrayOf(0f, 0f)

/** World size of the baked body canvas per boss (width, height). */
private fun canvasSize(kind: BossKind): Size = when (kind) {
    BossKind.Treant -> Size(420f, 300f)
    BossKind.Pharaoh -> Size(290f, 270f)
    BossKind.Dreadnought -> Size(470f, 200f)
    BossKind.VoidHeart -> Size(260f, 260f)
}

internal fun bakeBoss(kind: BossKind, scale: Float, density: Density): BossSprites {
    val cs = canvasSize(kind)
    val body = bake((cs.width * scale).roundToInt(), (cs.height * scale).roundToInt(), density) {
        val u = scale
        when (kind) {
            BossKind.Treant -> drawTreant(u)
            BossKind.Pharaoh -> drawPharaoh(u)
            BossKind.Dreadnought -> drawDreadnought(u)
            BossKind.VoidHeart -> drawVoidBody(u)
        }
    }
    val eyeColor = when (kind) {
        BossKind.Treant -> Color(0xFFFFC23A)
        BossKind.Pharaoh -> Color(0xFF2EE6C9)
        BossKind.Dreadnought -> Color(0xFFFF2E6E)
        BossKind.VoidHeart -> Color(0xFFC77DFF)
    }
    val eyeR = when (kind) {
        BossKind.VoidHeart -> 50f
        BossKind.Treant -> 20f
        else -> 16f
    }
    val eyeSize = (eyeR * 3f * scale).roundToInt()
    val eye = bake(eyeSize, eyeSize, density) { drawEyeGlow(eyeR * scale, eyeColor, kind == BossKind.VoidHeart) }
    val eyeRage = bake(eyeSize, eyeSize, density) { drawEyeGlow(eyeR * scale, Color(0xFFFF3B2E), kind == BossKind.VoidHeart) }
    val coreColor = when (kind) {
        BossKind.Treant -> Color(0xFF9CFF6B)
        BossKind.Pharaoh -> Color(0xFFFF3B3B)
        BossKind.Dreadnought -> Color(0xFFFF5CA8)
        BossKind.VoidHeart -> Color(0xFFFF7A3A)
    }
    val coreSize = (90f * scale).roundToInt()
    val core = bake(coreSize, coreSize, density) { drawCoreGlow(coreColor) }
    val extra = when (kind) {
        BossKind.Pharaoh -> {
            val ps = (PLATE_R * 2.8f * scale).roundToInt()
            bake(ps, ps, density) { drawScarab(PLATE_R * scale) }
        }
        BossKind.VoidHeart -> {
            val ss = (560f * scale).roundToInt()
            bake(ss, ss, density) { drawSwirl(scale) }
        }
        else -> core
    }
    val shotSize = (SHOT_R * 3.4f * scale).roundToInt()
    val shots = Array(SHOT_KIND_COUNT) { k -> bake(shotSize, shotSize, density) { drawShot(k, SHOT_R * scale) } }
    return BossSprites(kind, body, eye, eyeRage, core, extra, shots)
}

// Elder Treant

/** Treant body. */
private fun DrawScope.drawTreant(u: Float) {
    val c = center
    fun p(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)
    val rnd = Random(3)
    glowCircle(c.x, c.y + 40f * u, 150f * u, Color.Black.copy(alpha = 0.55f), 40f * u)

    // Arms
    val bark = Color(0xFF4A2E1A)
    for (side in -1..1 step 2) {
        val arm = Path()
        arm.moveTo(c.x + side * 40f * u, c.y + 10f * u)
        arm.cubicTo(c.x + side * 110f * u, c.y + 0f * u, c.x + side * 150f * u, c.y - 40f * u, c.x + side * 190f * u, c.y - 70f * u)
        drawPath(arm, bark, style = Stroke(width = 22f * u, cap = StrokeCap.Round))
        drawPath(arm, Color(0xFF7A5232), style = Stroke(width = 6f * u, cap = StrokeCap.Round), alpha = 0.6f)
        val twig = Path()
        twig.moveTo(c.x + side * 130f * u, c.y - 22f * u)
        twig.quadraticTo(c.x + side * 150f * u, c.y + 10f * u, c.x + side * 175f * u, c.y + 20f * u)
        drawPath(twig, bark, style = Stroke(width = 9f * u, cap = StrokeCap.Round))
        leafCluster(c.x + side * 188f * u, c.y - 74f * u, 30f * u, rnd)
        leafCluster(c.x + side * 172f * u, c.y + 22f * u, 18f * u, rnd)
    }

    // Trunk
    val trunk = Path()
    trunk.moveTo(p(-58f, -40f).x, p(-58f, -40f).y)
    trunk.cubicTo(p(-70f, 20f).x, p(-70f, 20f).y, p(-78f, 70f).x, p(-78f, 70f).y, p(-150f, 122f).x, p(-150f, 122f).y)
    trunk.lineTo(p(-95f, 112f).x, p(-95f, 112f).y)
    trunk.lineTo(p(-60f, 125f).x, p(-60f, 125f).y)
    trunk.lineTo(p(-20f, 108f).x, p(-20f, 108f).y)
    trunk.lineTo(p(15f, 126f).x, p(15f, 126f).y)
    trunk.lineTo(p(60f, 110f).x, p(60f, 110f).y)
    trunk.lineTo(p(105f, 124f).x, p(105f, 124f).y)
    trunk.lineTo(p(150f, 120f).x, p(150f, 120f).y)
    trunk.cubicTo(p(80f, 72f).x, p(80f, 72f).y, p(70f, 20f).x, p(70f, 20f).y, p(58f, -40f).x, p(58f, -40f).y)
    trunk.close()
    drawPath(
        trunk,
        Brush.horizontalGradient(
            listOf(Color(0xFF24160C), Color(0xFF5E3D24), Color(0xFF7A5232), Color(0xFF4A2E1A), Color(0xFF1C110A)),
            startX = c.x - 90f * u, endX = c.x + 90f * u,
        ),
    )
    clipPath(trunk) {
        for (k in 0 until 9) {
            val x0 = -60f + k * 15f
            val line = Path()
            line.moveTo(c.x + x0 * u, c.y - 40f * u)
            line.cubicTo(
                c.x + (x0 + 8f) * u, c.y + 10f * u,
                c.x + (x0 - 8f) * u, c.y + 60f * u,
                c.x + (x0 * 1.6f) * u, c.y + 125f * u,
            )
            drawPath(line, Color(0xFF1A0F07), alpha = 0.55f, style = Stroke(width = 2.5f * u))
        }
        // Moss
        repeat(26) {
            val mx = (rnd.nextFloat() - 0.5f) * 120f
            val my = -40f + rnd.nextFloat() * 30f
            drawCircle(Color(0xFF4E8F2E), (4f + rnd.nextFloat() * 7f) * u, p(mx, my), alpha = 0.7f)
        }
    }

    // Face
    for (side in -1..1 step 2) {
        val ex = side * 36f
        drawOval(Color(0xFF0E0804), p(ex - 22f, 4f), Size(44f * u, 28f * u))
        val brow = Path()
        brow.moveTo(p(ex - 30f * side, -2f).x, p(ex - 30f * side, -2f).y)
        brow.quadraticTo(p(ex, -14f).x, p(ex, -14f).y, p(ex + 26f * side, 2f).x, p(ex + 26f * side, 2f).y)
        drawPath(brow, Color(0xFF2A1A0E), style = Stroke(width = 9f * u, cap = StrokeCap.Round))
    }
    val mouth = Path()
    mouth.moveTo(p(-38f, 56f).x, p(-38f, 56f).y)
    for (k in 1..8) {
        val x = -38f + k * 9.5f
        val y = if (k % 2 == 0) 56f else 70f
        mouth.lineTo(p(x, y).x, p(x, y).y)
    }
    mouth.lineTo(p(28f, 80f).x, p(28f, 80f).y)
    mouth.lineTo(p(-26f, 82f).x, p(-26f, 82f).y)
    mouth.close()
    drawPath(mouth, Color(0xFF0E0804))

    // Leaves, dark layer first
    val blobs = 30
    for (k in 0 until blobs) {
        val a = k * 2.399963f
        val d = 22f + (k % 7) * 15f
        val bx = cos(a) * d * 1.55f
        val by = -70f + sin(a) * d * 0.55f
        val r = 30f + rnd.nextFloat() * 16f
        drawCircle(Color(0xFF0E2A12), r * u, p(bx, by + 6f))
    }
    for (k in 0 until blobs) {
        val a = k * 2.399963f
        val d = 22f + (k % 7) * 15f
        val bx = cos(a) * d * 1.55f
        val by = -74f + sin(a) * d * 0.55f
        val r = 26f + rnd.nextFloat() * 14f
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF6BC24A), Color(0xFF2F7A2A), Color(0xFF15401A)),
                p(bx - r * 0.35f, by - r * 0.4f), r * 1.4f * u,
            ),
            r * u, p(bx, by),
        )
    }
    // Berries
    repeat(9) {
        val bx = (rnd.nextFloat() - 0.5f) * 220f
        val by = -110f + rnd.nextFloat() * 70f
        glowCircle(p(bx, by).x, p(bx, by).y, 4f * u, Color(0xFFFF6A5C), 4f * u)
        drawCircle(Color(0xFFFFB0A0), 2.4f * u, p(bx, by))
    }
    // Mushrooms
    for (mx in floatArrayOf(-118f, -84f, 92f, 126f)) {
        glowCircle(p(mx, 112f).x, p(mx, 112f).y, 9f * u, Color(0xFF4FF0FF).copy(alpha = 0.7f), 8f * u)
        drawOval(Color(0xFFB8FBFF), p(mx - 8f, 106f), Size(16f * u, 8f * u))
        drawRect(Color(0xFFE8F4F0), p(mx - 2f, 112f), Size(4f * u, 8f * u))
    }
}

private fun DrawScope.leafCluster(x: Float, y: Float, r: Float, rnd: Random) {
    repeat(7) {
        val a = rnd.nextFloat() * 6.28f
        val d = rnd.nextFloat() * r * 0.7f
        val rr = r * (0.45f + rnd.nextFloat() * 0.35f)
        val cx = x + cos(a) * d
        val cy = y + sin(a) * d
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF7FD45A), Color(0xFF2F7A2A), Color(0xFF123A16)), Offset(cx - rr * 0.3f, cy - rr * 0.3f), rr * 1.4f),
            rr, Offset(cx, cy),
        )
    }
}

// Scarab Pharaoh

/** Pharaoh mask. */
private fun DrawScope.drawPharaoh(u: Float) {
    val c = center
    fun p(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)
    glowCircle(c.x, c.y + 20f * u, 125f * u, Color.Black.copy(alpha = 0.55f), 30f * u)
    glowCircle(c.x, c.y, 120f * u, Color(0xFFFFC94A).copy(alpha = 0.25f), 30f * u)

    // Collar
    val bands = arrayOf(Color(0xFF2EE6C9), Color(0xFFF2C14E), Color(0xFFC0392B), Color(0xFF1F4FB8), Color(0xFFF2C14E))
    for (k in bands.indices) {
        val r = 118f - k * 9f
        drawArc(bands[k], 20f, 140f, false, p(-r, 30f - r), Size(r * 2f * u, r * 2f * u), style = Stroke(width = 9f * u))
    }

    // Headdress stripes
    val nemes = Path()
    nemes.moveTo(p(-64f, -100f).x, p(-64f, -100f).y)
    nemes.quadraticTo(p(0f, -128f).x, p(0f, -128f).y, p(64f, -100f).x, p(64f, -100f).y)
    nemes.lineTo(p(122f, 30f).x, p(122f, 30f).y)
    nemes.lineTo(p(104f, 108f).x, p(104f, 108f).y)
    nemes.lineTo(p(64f, 108f).x, p(64f, 108f).y)
    nemes.lineTo(p(60f, 40f).x, p(60f, 40f).y)
    nemes.lineTo(p(-60f, 40f).x, p(-60f, 40f).y)
    nemes.lineTo(p(-64f, 108f).x, p(-64f, 108f).y)
    nemes.lineTo(p(-104f, 108f).x, p(-104f, 108f).y)
    nemes.lineTo(p(-122f, 30f).x, p(-122f, 30f).y)
    nemes.close()
    clipPath(nemes) {
        drawRect(Color(0xFFE8B23A), p(-130f, -130f), Size(260f * u, 250f * u))
        var y = -128f
        var k = 0
        while (y < 120f) {
            if (k % 2 == 1) drawRect(Color(0xFF1F4FB8), p(-130f, y), Size(260f * u, 9f * u))
            y += 9f
            k++
        }
        drawRect(
            Brush.horizontalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0x66000000)), startX = c.x - 120f * u, endX = c.x + 120f * u),
            p(-130f, -130f), Size(260f * u, 250f * u),
        )
    }
    drawPath(nemes, Color(0xFF8A5A12), style = Stroke(width = 3f * u))

    // Face
    val face = Path()
    face.addRoundRect(RoundRect(Rect(p(-60f, -86f), p(60f, 70f)), CornerRadius(52f * u, 60f * u)))
    drawPath(
        face,
        Brush.radialGradient(listOf(Color(0xFFFFF0B8), Color(0xFFF2C14E), Color(0xFFB07A1E)), p(-18f, -40f), 120f * u),
    )
    drawPath(face, Color(0xFF8A5A12), style = Stroke(width = 2.5f * u))
    // Eyes
    for (side in -1..1 step 2) {
        val ex = side * 34f
        val eye = Path()
        eye.moveTo(p(ex - 22f, -8f).x, p(ex - 22f, -8f).y)
        eye.quadraticTo(p(ex, -24f).x, p(ex, -24f).y, p(ex + 22f, -8f).x, p(ex + 22f, -8f).y)
        eye.quadraticTo(p(ex, 8f).x, p(ex, 8f).y, p(ex - 22f, -8f).x, p(ex - 22f, -8f).y)
        eye.close()
        drawPath(eye, Color(0xFF07121A))
        val wingStart = if (side < 0) p(ex - 22f, -8f) else p(ex + 22f, -8f)
        drawLine(Color(0xFF07121A), wingStart, Offset(wingStart.x + side * 22f * u, wingStart.y + 8f * u), strokeWidth = 4f * u, cap = StrokeCap.Round)
        drawLine(Color(0xFF07121A), p(ex - 20f, -30f), p(ex + 20f, -32f), strokeWidth = 4f * u, cap = StrokeCap.Round)
    }
    drawLine(Color(0xFFB07A1E), p(0f, -8f), p(-5f, 26f), strokeWidth = 3f * u, cap = StrokeCap.Round)
    drawLine(Color(0xFFB07A1E), p(-8f, 28f), p(6f, 28f), strokeWidth = 3f * u, cap = StrokeCap.Round)
    drawOval(Color(0xFF9A5A10), p(-18f, 40f), Size(36f * u, 10f * u))

    // Beard
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF1F4FB8), Color(0xFF4F86FF), Color(0xFF1F4FB8)), startX = c.x - 14f * u, endX = c.x + 14f * u),
        p(-14f, 66f), Size(28f * u, 46f * u), CornerRadius(8f * u),
    )
    var by = 72f
    while (by < 110f) {
        drawLine(Color(0xFFF2C14E), p(-12f, by), p(12f, by + 4f), strokeWidth = 2.5f * u)
        by += 8f
    }

    // Cobra
    val hood = Path()
    hood.moveTo(p(0f, -122f).x, p(0f, -122f).y)
    hood.cubicTo(p(16f, -118f).x, p(16f, -118f).y, p(14f, -96f).x, p(14f, -96f).y, p(0f, -88f).x, p(0f, -88f).y)
    hood.cubicTo(p(-14f, -96f).x, p(-14f, -96f).y, p(-16f, -118f).x, p(-16f, -118f).y, p(0f, -122f).x, p(0f, -122f).y)
    drawPath(hood, Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFC08A1E)), startY = c.y - 122f * u, endY = c.y - 88f * u))
    drawCircle(Color(0xFFC0392B), 4.5f * u, p(0f, -106f))
}

/** Scarab shield. */
private fun DrawScope.drawScarab(r: Float) {
    val c = center
    glowCircle(c.x, c.y, r * 1.05f, Color(0xFF2EE6C9).copy(alpha = 0.7f), r * 0.4f)
    for (side in -1..1 step 2) {
        for (k in -1..1) {
            drawLine(
                Color(0xFF3A2A10),
                Offset(c.x + side * r * 0.4f, c.y + k * r * 0.35f),
                Offset(c.x + side * r * 1.05f, c.y + k * r * 0.5f + r * 0.1f),
                strokeWidth = r * 0.1f, cap = StrokeCap.Round,
            )
        }
    }
    drawOval(Brush.radialGradient(listOf(Color(0xFFFFE08A), Color(0xFFC08A1E)), Offset(c.x, c.y - r * 0.9f), r * 0.5f), Offset(c.x - r * 0.35f, c.y - r * 1.02f), Size(r * 0.7f, r * 0.42f))
    drawOval(
        Brush.radialGradient(listOf(Color(0xFF9FFFF0), Color(0xFF14A38E), Color(0xFF07473F)), Offset(c.x - r * 0.3f, c.y - r * 0.4f), r * 1.3f),
        Offset(c.x - r * 0.72f, c.y - r * 0.72f), Size(r * 1.44f, r * 1.6f),
    )
    drawLine(Color(0xFF053A33), Offset(c.x, c.y - r * 0.7f), Offset(c.x, c.y + r * 0.86f), strokeWidth = r * 0.07f)
    drawArc(Color(0xFFF2C14E), 200f, 140f, false, Offset(c.x - r * 0.72f, c.y - r * 0.72f), Size(r * 1.44f, r * 1.6f), style = Stroke(width = r * 0.1f))
    drawOval(Color.White.copy(alpha = 0.45f), Offset(c.x - r * 0.5f, c.y - r * 0.55f), Size(r * 0.3f, r * 0.55f))
}

// Dreadnought

/** Dreadnought hull. */
private fun DrawScope.drawDreadnought(u: Float) {
    val c = center
    fun p(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)
    glowRoundRect(c.x - 200f * u, c.y - 40f * u, c.x + 200f * u, c.y + 70f * u, 60f * u, Color.Black.copy(alpha = 0.6f), 26f * u)

    // Dome
    drawOval(
        Brush.radialGradient(listOf(Color(0xFFE8FDFF), Color(0xFF4FC8F0), Color(0xFF0A3A5A)), p(-18f, -78f), 70f * u),
        p(-56f, -86f), Size(112f * u, 60f * u),
    )
    drawOval(Color.White.copy(alpha = 0.5f), p(-36f, -80f), Size(30f * u, 12f * u))

    val pts = floatArrayOf(-205f, 0f, -150f, -40f, -60f, -55f, 60f, -55f, 150f, -40f, 205f, 0f, 150f, 45f, 60f, 62f, -60f, 62f, -150f, 45f)
    val hull = Path()
    for (k in pts.indices step 2) {
        val o = p(pts[k], pts[k + 1])
        if (k == 0) hull.moveTo(o.x, o.y) else hull.lineTo(o.x, o.y)
    }
    hull.close()
    drawPath(hull, Brush.verticalGradient(listOf(Color(0xFFC8D0DA), Color(0xFF6A7380), Color(0xFF3A414B), Color(0xFF1A1E24)), startY = c.y - 55f * u, endY = c.y + 62f * u))
    clipPath(hull) {
        // Panels
        drawRect(Color.White.copy(alpha = 0.12f), p(-210f, -60f), Size(420f * u, 40f * u))
        for (x in floatArrayOf(-150f, -100f, -60f, 0f, 60f, 100f, 150f)) {
            drawLine(Color(0xFF15181D), p(x, -60f), p(x * 0.9f, 70f), strokeWidth = 1.6f * u, alpha = 0.6f)
        }
        drawLine(Color(0xFF15181D), p(-210f, 6f), p(210f, 6f), strokeWidth = 2f * u, alpha = 0.6f)
        // Stripes
        drawRect(Color(0xFFE0284A), p(-190f, -10f), Size(70f * u, 7f * u))
        drawRect(Color(0xFFE0284A), p(120f, -10f), Size(70f * u, 7f * u))
    }
    drawPath(hull, Color(0xFFE8EDF2), alpha = 0.5f, style = Stroke(width = 2f * u))
    // Windows
    for (k in -5..5) drawRect(Color(0xFF7DF3FF), p(k * 14f - 4f, -30f), Size(8f * u, 5f * u), alpha = 0.85f)
    // Cannons
    for (side in -1..1 step 2) {
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF2A2E36), Color(0xFF8C96A3), Color(0xFF2A2E36)), startX = c.x + (side * 150f - 14f) * u, endX = c.x + (side * 150f + 14f) * u),
            p(side * 150f - 14f, 20f), Size(28f * u, 34f * u), CornerRadius(6f * u),
        )
        drawCircle(Color(0xFF15181D), 8f * u, p(side * 150f, 50f))
    }
    // Hatch (weak point)
    drawOval(Color(0xFF0A0B0E), p(-46f, 38f), Size(92f * u, 28f * u))
    drawOval(Color(0xFFB8C4D0), p(-46f, 38f), Size(92f * u, 28f * u), style = Stroke(width = 3f * u))
}

// The Void Heart

/** Black hole core. */
private fun DrawScope.drawVoidBody(u: Float) {
    val c = center
    val r = 100f * u
    glowCircle(c.x, c.y, r * 1.05f, Color(0xFFFF9A4A).copy(alpha = 0.9f), r * 0.18f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF1A0630)), c, r), r, c)
    drawCircle(Color(0xFFFFE0B0), r, c, style = Stroke(width = 2.5f * u))
    drawCircle(Color(0xFFC77DFF), r * 0.94f, c, alpha = 0.35f, style = Stroke(width = 4f * u))
}

/** Accretion swirl, rotated at draw time. */
private fun DrawScope.drawSwirl(u: Float) {
    val c = center
    for (arm in 0 until 3) {
        val path = Path()
        var first = true
        var t = 0f
        while (t <= 1f) {
            val a = arm * (2f * PI.toFloat() / 3f) + t * 3.6f
            val rr = (110f + t * 160f) * u
            val x = c.x + cos(a) * rr
            val y = c.y + sin(a) * rr
            if (first) path.moveTo(x, y) else path.lineTo(x, y)
            first = false
            t += 0.02f
        }
        glowPath(path, Color(0xFFB04BFF).copy(alpha = 0.45f), 26f * u, strokeWidth = 36f * u)
        glowPath(path, Color(0xFFFF7A3A).copy(alpha = 0.5f), 8f * u, strokeWidth = 12f * u)
        glowPath(path, Color(0xFFFFE0B0).copy(alpha = 0.7f), 1.5f * u, strokeWidth = 3f * u)
    }
    glowCircle(c.x, c.y, 150f * u, Color(0xFF7A2EFF).copy(alpha = 0.35f), 60f * u)
}

// Shared parts

private fun DrawScope.drawEyeGlow(r: Float, color: Color, iris: Boolean) {
    val c = center
    glowCircle(c.x, c.y, r * 1.1f, color.copy(alpha = 0.85f), r * 0.5f)
    if (iris) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6F8), color, dark(color, 0.6f)), c, r), r, c)
        for (k in 0 until 24) {
            val a = k * (2f * PI.toFloat() / 24f)
            drawLine(dark(color, 0.5f), Offset(c.x + cos(a) * r * 0.35f, c.y + sin(a) * r * 0.35f), Offset(c.x + cos(a) * r * 0.95f, c.y + sin(a) * r * 0.95f), strokeWidth = r * 0.04f, alpha = 0.6f)
        }
    } else {
        drawCircle(Brush.radialGradient(listOf(Color.White, light(color, 0.4f), color), c, r * 0.8f), r * 0.75f, c)
    }
}

private fun DrawScope.drawCoreGlow(color: Color) {
    val c = center
    val r = size.width * 0.22f
    glowCircle(c.x, c.y, r * 1.5f, color.copy(alpha = 0.7f), r)
    drawCircle(Brush.radialGradient(listOf(Color.White, light(color, 0.3f), color), c, r), r, c)
}

/** One sprite per shot kind. */
private fun DrawScope.drawShot(kind: Int, r: Float) {
    val c = center
    val col = BrickPalette.shot[kind]
    glowCircle(c.x, c.y, r * 1.1f, col.copy(alpha = 0.8f), r * 0.6f)
    when (kind) {
        SHOT_SEED -> {
            drawOval(Brush.radialGradient(listOf(Color(0xFFD9A066), Color(0xFF8A5222), Color(0xFF4A2A10)), Offset(c.x - r * 0.3f, c.y), r * 1.2f), Offset(c.x - r * 0.62f, c.y - r * 0.5f), Size(r * 1.24f, r * 1.4f))
            drawArc(Color(0xFF5A3A1A), 180f, 180f, true, Offset(c.x - r * 0.75f, c.y - r * 0.85f), Size(r * 1.5f, r * 0.9f))
            drawLine(Color(0xFF5A3A1A), Offset(c.x, c.y - r * 0.85f), Offset(c.x + r * 0.2f, c.y - r * 1.15f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
        }
        SHOT_ANKH -> {
            val gold = Color(0xFFFFD23A)
            val sw = r * 0.26f
            drawOval(gold, Offset(c.x - r * 0.38f, c.y - r * 0.95f), Size(r * 0.76f, r * 0.8f), style = Stroke(width = sw))
            drawLine(gold, Offset(c.x - r * 0.7f, c.y - r * 0.05f), Offset(c.x + r * 0.7f, c.y - r * 0.05f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(gold, Offset(c.x, c.y - r * 0.1f), Offset(c.x, c.y + r * 0.95f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(Color.White, r * 1.25f, c, alpha = 0.7f, style = Stroke(width = r * 0.08f))
        }
        SHOT_STAR -> {
            val star = Path()
            for (k in 0 until 8) {
                val a = k * (PI.toFloat() / 4f) - PI.toFloat() / 2f
                val rr = if (k % 2 == 0) r * 1.05f else r * 0.36f
                val x = c.x + cos(a) * rr
                val y = c.y + sin(a) * rr
                if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, Brush.radialGradient(listOf(Color.White, Color(0xFFFFF3A0), Color(0xFFFFB02E)), c, r))
            drawCircle(Color.White, r * 1.25f, c, alpha = 0.7f, style = Stroke(width = r * 0.08f))
        }
        SHOT_SAND, SHOT_PLASMA -> {
            drawCircle(Brush.radialGradient(listOf(Color.White, light(col, 0.3f), col, dark(col, 0.4f)), Offset(c.x - r * 0.2f, c.y - r * 0.2f), r), r * 0.78f, c)
            if (kind == SHOT_SAND) drawArc(Color(0xFFFFE6B0), 30f, 200f, false, Offset(c.x - r * 0.5f, c.y - r * 0.5f), Size(r, r), alpha = 0.7f, style = Stroke(width = r * 0.12f))
        }
        else -> {
            drawCircle(Color(0xFF07020F), r * 0.75f, c)
            drawCircle(col, r * 0.75f, c, style = Stroke(width = r * 0.2f))
            drawCircle(Color(0xFFFF9AEA), r * 0.2f, Offset(c.x - r * 0.2f, c.y - r * 0.2f), alpha = 0.8f)
        }
    }
}

/** Degrees to point a sprite outwards at [angle]. */
internal fun outwardDegrees(angle: Float): Float = angle * 180f / PI.toFloat() + 90f

/** Draws [image] centred at ([x], [y]), optionally rotated. */
internal fun DrawScope.drawCentered(image: ImageBitmap, x: Float, y: Float, alpha: Float = 1f, degrees: Float = 0f, colorFilter: ColorFilter? = null) {
    if (degrees == 0f) {
        drawImage(image, Offset(x - image.width * 0.5f, y - image.height * 0.5f), alpha = alpha, colorFilter = colorFilter)
    } else {
        rotate(degrees, Offset(x, y)) {
            drawImage(image, Offset(x - image.width * 0.5f, y - image.height * 0.5f), alpha = alpha, colorFilter = colorFilter)
        }
    }
}
