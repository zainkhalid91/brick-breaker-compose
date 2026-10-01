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

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.zainkhalid.brickbreaker.engine.BALL_R
import com.zainkhalid.brickbreaker.engine.BRICK_GAP
import com.zainkhalid.brickbreaker.engine.BossKind
import com.zainkhalid.brickbreaker.engine.BrickKind
import com.zainkhalid.brickbreaker.engine.BrickStyle
import com.zainkhalid.brickbreaker.engine.BrickTheme
import com.zainkhalid.brickbreaker.engine.CELL_H
import com.zainkhalid.brickbreaker.engine.CELL_W
import com.zainkhalid.brickbreaker.engine.CEILING
import com.zainkhalid.brickbreaker.engine.DROP_H
import com.zainkhalid.brickbreaker.engine.DRONE_R
import com.zainkhalid.brickbreaker.engine.DROP_W
import com.zainkhalid.brickbreaker.engine.MOVER_H
import com.zainkhalid.brickbreaker.engine.PADDLE_H
import com.zainkhalid.brickbreaker.engine.PowerUp
import com.zainkhalid.brickbreaker.engine.WALL
import com.zainkhalid.brickbreaker.engine.WORLD_W
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Bitmaps for one theme. */
class BrickSprites(
    val theme: BrickTheme,
    val brickW: Int,
    val brickH: Int,
    val brickPad: Int,
    val bricks: Array<ImageBitmap>,
    val cracks: Array<ImageBitmap>,
    val ball: ImageBitmap,
    val fireBall: ImageBitmap,
    val paddle: ImageBitmap,
    val paddleCap: Int,
    val paddlePad: Int,
    val drops: Array<ImageBitmap>,
    val drone: ImageBitmap,
    val moverBrush: Brush,
    val ringStroke: Stroke,
    val accent: Color,
    val accent2: Color,
)

/** All baked bitmaps. Backgrounds are big, so they're baked on demand and only two are kept. */
class BrickAssets private constructor(
    private val sprites: Array<BrickSprites>,
    private val bosses: Array<BossSprites>,
    private val width: Int,
    private val height: Int,
    private val scale: Float,
    private val density: Density,
) {
    private val backgrounds = LinkedHashMap<BrickTheme, ImageBitmap>(4, 0.75f, true)

    fun sprites(theme: BrickTheme): BrickSprites = sprites[theme.ordinal]

    fun boss(kind: BossKind): BossSprites = bosses[kind.ordinal]

    /** The backdrop for [theme], baking it if needed. */
    fun background(theme: BrickTheme): ImageBitmap = synchronized(backgrounds) {
        backgrounds[theme] ?: bakeBackground(theme, width, height, scale, density).also {
            backgrounds[theme] = it
            while (backgrounds.size > 2) backgrounds.remove(backgrounds.keys.first())
        }
    }

    companion object {
        /** Bakes all sprites for a playfield of `width × height` px. */
        fun bake(width: Int, height: Int, density: Density, textMeasurer: TextMeasurer): BrickAssets {
            val scale = width / WORLD_W
            val dw = DROP_W * scale
            val dh = DROP_H * scale
            val dp = (dh * 0.55f).roundToInt()
            val drops = Array(PowerUp.entries.size) { k ->
                bake((dw + dp * 2).roundToInt(), (dh + dp * 2).roundToInt(), density) {
                    drawCapsule(PowerUp.entries[k], dp.toFloat(), dw, dh, textMeasurer)
                }
            }
            val droneR = DRONE_R * scale
            val droneSize = (droneR * 3.4f).roundToInt()
            val drone = bake(droneSize, droneSize, density) { drawDrone(droneR) }
            val styles = Array(BrickStyle.entries.size) { bakeStyle(BrickStyle.entries[it], scale, density) }
            val ringStroke = Stroke(width = 3.5f * scale)
            val themes = BrickTheme.entries
            return BrickAssets(
                sprites = Array(themes.size) { i ->
                    val theme = themes[i]
                    val set = styles[theme.style.ordinal]
                    BrickSprites(
                        theme = theme,
                        brickW = set.brickW,
                        brickH = set.brickH,
                        brickPad = set.brickPad,
                        bricks = set.bricks,
                        cracks = set.cracks,
                        ball = set.ball,
                        fireBall = set.fireBall,
                        paddle = set.paddle,
                        paddleCap = set.paddleCap,
                        paddlePad = set.paddlePad,
                        drops = drops,
                        drone = drone,
                        moverBrush = set.moverBrush,
                        ringStroke = ringStroke,
                        accent = BrickPalette.accent(theme),
                        accent2 = BrickPalette.accent2(theme),
                    )
                },
                bosses = Array(BossKind.entries.size) { bakeBoss(BossKind.entries[it], scale, density) },
                width = width,
                height = height,
                scale = scale,
                density = density,
            )
        }
    }
}

/** Bitmaps shared by every theme of one [BrickStyle]. */
private class BrickStyleSet(
    val brickW: Int,
    val brickH: Int,
    val brickPad: Int,
    val bricks: Array<ImageBitmap>,
    val cracks: Array<ImageBitmap>,
    val ball: ImageBitmap,
    val fireBall: ImageBitmap,
    val paddle: ImageBitmap,
    val paddleCap: Int,
    val paddlePad: Int,
    val moverBrush: Brush,
)

// Baking primitives

internal fun bake(width: Int, height: Int, density: Density, block: DrawScope.() -> Unit): ImageBitmap {
    val bitmap = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
    CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        Canvas(bitmap),
        Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
    ) { block() }
    bitmap.prepareToDraw()
    return bitmap
}

/** Blurred glow using BlurMaskFilter. */
internal fun DrawScope.glowRoundRect(
    left: Float, top: Float, right: Float, bottom: Float,
    radius: Float, color: Color, blur: Float, strokeWidth: Float = 0f,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color.toArgb()
        if (blur > 0.5f) paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        if (strokeWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
        }
        canvas.nativeCanvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }
}

internal fun DrawScope.glowCircle(cx: Float, cy: Float, radius: Float, color: Color, blur: Float) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color.toArgb()
        if (blur > 0.5f) paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        canvas.nativeCanvas.drawCircle(cx, cy, radius, paint)
    }
}

internal fun light(c: Color, t: Float): Color = lerp(c, Color.White, t)
internal fun dark(c: Color, t: Float): Color = lerp(c, Color.Black, t)

/** Same, for a path. */
internal fun DrawScope.glowPath(path: Path, color: Color, blur: Float, strokeWidth: Float = 0f) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color.toArgb()
        if (blur > 0.5f) paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        if (strokeWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

// Sprites

private fun bakeStyle(style: BrickStyle, scale: Float, density: Density): BrickStyleSet {
    val bw = ((CELL_W - BRICK_GAP) * scale).roundToInt()
    val bh = ((CELL_H - BRICK_GAP) * scale).roundToInt()
    val pad = if (style == BrickStyle.Neon) (bh * 0.45f).roundToInt() else (bh * 0.3f).roundToInt().coerceAtLeast(3)

    val bricks = Array(BrickKind.COUNT) { kind ->
        bake(bw + pad * 2, bh + pad * 2, density) { drawBrick(kind, style, pad.toFloat(), bw.toFloat(), bh.toFloat()) }
    }
    val cracks = Array(2) { level -> bake(bw, bh, density) { drawCrack(level, bw.toFloat(), bh.toFloat()) } }

    val ballR = BALL_R * scale
    val ballSize = (ballR * 5.2f).roundToInt()
    val ball = bake(ballSize, ballSize, density) { drawBall(style, ballR, fire = false) }
    val fireBall = bake(ballSize, ballSize, density) { drawBall(style, ballR, fire = true) }

    val ph = PADDLE_H * scale
    val cap = ph * 1.1f
    val pp = (ph * 0.6f).roundToInt()
    val paddleW = (cap * 2f + ph * 2f).roundToInt() + pp * 2
    val paddle = bake(paddleW, (ph + pp * 2).roundToInt(), density) { drawPaddle(style, pp.toFloat(), ph, cap) }

    val moverBrush = Brush.verticalGradient(
        when (style) {
            BrickStyle.Glossy -> listOf(Color(0xFFE9D8A6), Color(0xFF9C7A3C), Color(0xFF5A4318))
            BrickStyle.Sandstone -> listOf(Color(0xFFFFE3A3), Color(0xFFC8923B), Color(0xFF6B4512))
            BrickStyle.Frost -> listOf(Color(0xFFF2FDFF), Color(0xFF8FD8F0), Color(0xFF2F6E8C))
            BrickStyle.Neon -> listOf(Color(0xFFFFB3F0), Color(0xFFFF2BD6), Color(0xFF6A0A5A))
        },
        startY = 0f,
        endY = MOVER_H * scale,
    )

    return BrickStyleSet(
        brickW = bw,
        brickH = bh,
        brickPad = pad,
        bricks = bricks,
        cracks = cracks,
        ball = ball,
        fireBall = fireBall,
        paddle = paddle,
        paddleCap = (pp + cap).roundToInt(),
        paddlePad = pp,
        moverBrush = moverBrush,
    )
}

private fun DrawScope.drawBrick(kind: Int, style: BrickStyle, pad: Float, w: Float, h: Float) {
    val base = BrickPalette.brick[kind]
    if (kind == BrickKind.STEEL) {
        drawSteel(style, pad, w, h)
        return
    }
    when (style) {
        BrickStyle.Glossy -> drawGlossy(base, pad, w, h)
        BrickStyle.Sandstone -> drawSandstone(base, kind, pad, w, h)
        BrickStyle.Frost -> drawFrost(base, pad, w, h)
        BrickStyle.Neon -> drawNeon(base, pad, w, h)
    }
    if (kind == BrickKind.SILVER || kind == BrickKind.GOLD) drawToughBar(style, base, pad, w, h)
    if (kind == BrickKind.TNT) drawHazard(pad, w, h)
}

/** Candy-glossy brick (forest and jungle). */
private fun DrawScope.drawGlossy(base: Color, pad: Float, w: Float, h: Float) {
    val r = h * 0.22f
    glowRoundRect(pad, pad + h * 0.14f, pad + w, pad + h + h * 0.14f, r, Color.Black.copy(alpha = 0.55f), pad * 0.8f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(light(base, 0.38f), base, dark(base, 0.3f)), startY = pad, endY = pad + h),
        topLeft = Offset(pad, pad),
        size = Size(w, h),
        cornerRadius = CornerRadius(r),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.06f)),
            startY = pad, endY = pad + h * 0.5f,
        ),
        topLeft = Offset(pad + h * 0.14f, pad + h * 0.08f),
        size = Size(w - h * 0.28f, h * 0.4f),
        cornerRadius = CornerRadius(r * 0.8f),
    )
    drawRoundRect(
        color = light(base, 0.55f).copy(alpha = 0.6f),
        topLeft = Offset(pad, pad),
        size = Size(w, h),
        cornerRadius = CornerRadius(r),
        style = Stroke(width = max(1f, h * 0.045f)),
    )
}

/** Bevelled block, the base of the sandstone style. */
private fun DrawScope.drawBevel(base: Color, pad: Float, w: Float, h: Float) {
    val e = h * 0.2f
    val l = pad
    val t = pad
    val r = pad + w
    val b = pad + h
    glowRoundRect(l, t + e * 0.8f, r, b + e * 0.8f, 2f, Color.Black.copy(alpha = 0.75f), pad * 0.7f)
    drawRect(base, Offset(l, t), Size(w, h))
    val path = Path()
    path.moveTo(l, t); path.lineTo(r, t); path.lineTo(r - e, t + e); path.lineTo(l + e, t + e); path.close()
    drawPath(path, light(base, 0.5f))
    path.reset()
    path.moveTo(l, t); path.lineTo(l + e, t + e); path.lineTo(l + e, b - e); path.lineTo(l, b); path.close()
    drawPath(path, light(base, 0.22f))
    path.reset()
    path.moveTo(l, b); path.lineTo(l + e, b - e); path.lineTo(r - e, b - e); path.lineTo(r, b); path.close()
    drawPath(path, dark(base, 0.45f))
    path.reset()
    path.moveTo(r, t); path.lineTo(r, b); path.lineTo(r - e, b - e); path.lineTo(r - e, t + e); path.close()
    drawPath(path, dark(base, 0.28f))
    drawRect(
        brush = Brush.verticalGradient(listOf(light(base, 0.16f), dark(base, 0.08f)), startY = t + e, endY = b - e),
        topLeft = Offset(l + e, t + e),
        size = Size(w - 2 * e, h - 2 * e),
    )
    drawRect(Color.White.copy(alpha = 0.3f), Offset(l + e, t + e), Size(w - 2 * e, (h - 2 * e) * 0.2f))
}

/** Sandstone brick (desert). */
private fun DrawScope.drawSandstone(base: Color, kind: Int, pad: Float, w: Float, h: Float) {
    val tinted = lerp(base, Color(0xFFE2B66E), 0.42f)
    drawBevel(tinted, pad, w, h)
    val e = h * 0.2f
    val rnd = Random(kind * 31 + 7)
    repeat(26) {
        drawCircle(
            if (rnd.nextBoolean()) dark(tinted, 0.35f) else light(tinted, 0.4f),
            radius = h * (0.015f + rnd.nextFloat() * 0.025f),
            center = Offset(pad + e + rnd.nextFloat() * (w - 2 * e), pad + e + rnd.nextFloat() * (h - 2 * e)),
            alpha = 0.55f,
        )
    }
    val inset = e * 1.35f
    drawRect(
        dark(tinted, 0.45f), Offset(pad + inset, pad + inset), Size(w - inset * 2, h - inset * 2),
        alpha = 0.45f, style = Stroke(width = max(1f, h * 0.035f)),
    )
    // Engraved eye, drawn twice for a carved look.
    val cx = pad + w / 2f
    val cy = pad + h / 2f
    for (pass in 0..1) {
        val dy = if (pass == 0) h * 0.03f else 0f
        val col = if (pass == 0) light(tinted, 0.5f) else dark(tinted, 0.55f)
        drawOval(col, Offset(cx - h * 0.3f, cy - h * 0.13f + dy), Size(h * 0.6f, h * 0.26f), alpha = 0.7f, style = Stroke(width = max(1f, h * 0.04f)))
        drawCircle(col, h * 0.07f, Offset(cx, cy + dy), alpha = 0.8f)
    }
}

/** Ice brick. */
private fun DrawScope.drawFrost(base: Color, pad: Float, w: Float, h: Float) {
    val l = pad
    val t = pad
    val r = h * 0.14f
    val ice = lerp(base, Color(0xFFDDF6FF), 0.35f)
    glowRoundRect(l, t, l + w, t + h, r, ice.copy(alpha = 0.45f), pad * 0.8f)
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(light(ice, 0.7f).copy(alpha = 0.95f), ice.copy(alpha = 0.82f), dark(ice, 0.25f).copy(alpha = 0.88f)),
            startY = t, endY = t + h,
        ),
        topLeft = Offset(l, t),
        size = Size(w, h),
        cornerRadius = CornerRadius(r),
    )
    clipRect(l, t, l + w, t + h) {
        val facet = Path()
        facet.moveTo(l, t); facet.lineTo(l + w * 0.42f, t); facet.lineTo(l + w * 0.18f, t + h); facet.lineTo(l, t + h); facet.close()
        drawPath(facet, Color.White, alpha = 0.22f)
        facet.reset()
        facet.moveTo(l + w * 0.62f, t); facet.lineTo(l + w * 0.74f, t); facet.lineTo(l + w * 0.5f, t + h); facet.lineTo(l + w * 0.38f, t + h); facet.close()
        drawPath(facet, Color.White, alpha = 0.16f)
        facet.reset()
        facet.moveTo(l + w, t + h * 0.35f); facet.lineTo(l + w, t + h); facet.lineTo(l + w * 0.7f, t + h); facet.close()
        drawPath(facet, dark(ice, 0.5f), alpha = 0.25f)
        drawLine(Color.White, Offset(l + w * 0.12f, t + h * 0.8f), Offset(l + w * 0.3f, t + h * 0.2f), strokeWidth = max(1f, h * 0.035f), alpha = 0.7f)
    }
    drawRoundRect(Color.White, Offset(l, t), Size(w, h), CornerRadius(r), alpha = 0.75f, style = Stroke(width = max(1f, h * 0.05f)))
    // Sparkle
    val sx = l + w * 0.86f
    val sy = t + h * 0.26f
    val sr = h * 0.18f
    drawLine(Color.White, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = max(1f, h * 0.03f), cap = StrokeCap.Round)
    drawLine(Color.White, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = max(1f, h * 0.03f), cap = StrokeCap.Round)
    glowCircle(sx, sy, sr * 0.35f, Color.White, sr * 0.4f)
}

/** Neon tube brick (space). */
private fun DrawScope.drawNeon(base: Color, pad: Float, w: Float, h: Float) {
    val r = h * 0.16f
    val l = pad
    val t = pad
    glowRoundRect(l, t, l + w, t + h, r, base.copy(alpha = 0.95f), pad * 0.5f, strokeWidth = h * 0.14f)
    drawRoundRect(base.copy(alpha = 0.16f), Offset(l, t), Size(w, h), CornerRadius(r))
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(base.copy(alpha = 0.32f), Color.Transparent), startY = t, endY = t + h),
        topLeft = Offset(l, t),
        size = Size(w, h),
        cornerRadius = CornerRadius(r),
    )
    drawRoundRect(base, Offset(l, t), Size(w, h), CornerRadius(r), style = Stroke(width = h * 0.08f))
    drawRoundRect(light(base, 0.7f), Offset(l, t), Size(w, h), CornerRadius(r), style = Stroke(width = h * 0.03f))
}

/** Indestructible gunmetal plate with rivets. */
private fun DrawScope.drawSteel(style: BrickStyle, pad: Float, w: Float, h: Float) {
    val l = pad
    val t = pad
    val r = h * 0.12f
    if (style == BrickStyle.Neon) {
        glowRoundRect(l, t, l + w, t + h, r, Color(0xFF8FA3BF), pad * 0.4f, strokeWidth = h * 0.1f)
    } else {
        glowRoundRect(l, t + h * 0.15f, l + w, t + h * 1.15f, r, Color.Black.copy(alpha = 0.7f), pad * 0.7f)
    }
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFF9AA4B1), Color(0xFF4A525E), Color(0xFF79828F), Color(0xFF363C46)),
            start = Offset(l, t), end = Offset(l + w, t + h),
        ),
        topLeft = Offset(l, t),
        size = Size(w, h),
        cornerRadius = CornerRadius(r),
    )
    drawRoundRect(Color(0xFF1C2027), Offset(l, t), Size(w, h), CornerRadius(r), style = Stroke(width = max(1f, h * 0.05f)))
    val rv = h * 0.09f
    val inset = h * 0.2f
    for (cx in floatArrayOf(l + inset, l + w - inset)) {
        for (cy in floatArrayOf(t + inset, t + h - inset)) {
            drawCircle(Color(0xFF2A2F37), rv * 1.2f, Offset(cx, cy + rv * 0.3f))
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFE6EBF0), Color(0xFF6D7682)), Offset(cx - rv * 0.3f, cy - rv * 0.3f), rv * 1.4f),
                radius = rv,
                center = Offset(cx, cy),
            )
        }
    }
    clipRect(l, t, l + w, t + h) {
        drawLine(Color.White.copy(alpha = 0.22f), Offset(l + w * 0.3f, t + h), Offset(l + w * 0.45f, t), strokeWidth = h * 0.18f)
    }
}

/** Glowing inner bar that marks multi-hit bricks (silver, gold). */
private fun DrawScope.drawToughBar(style: BrickStyle, base: Color, pad: Float, w: Float, h: Float) {
    val bw = w * 0.52f
    val bh = h * 0.2f
    val x = pad + (w - bw) / 2f
    val y = pad + (h - bh) / 2f
    if (style == BrickStyle.Neon) {
        glowRoundRect(x, y, x + bw, y + bh, bh / 2f, light(base, 0.3f), bh * 0.8f)
    }
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.15f)),
            startX = x, endX = x + bw,
        ),
        topLeft = Offset(x, y),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(bh / 2f),
    )
}

/** Hazard stripes and a lit bomb on the TNT brick. */
private fun DrawScope.drawHazard(pad: Float, w: Float, h: Float) {
    val l = pad
    val t = pad
    clipRect(l + h * 0.1f, t + h * 0.1f, l + w - h * 0.1f, t + h - h * 0.1f) {
        var x = l - h
        while (x < l + w + h) {
            drawLine(Color.Black.copy(alpha = 0.45f), Offset(x, t + h), Offset(x + h, t), strokeWidth = h * 0.22f)
            x += h * 0.55f
        }
    }
    val cx = l + w / 2f
    val cy = t + h / 2f
    val r = h * 0.26f
    drawCircle(Color(0xFF15161A), r * 1.15f, Offset(cx, cy))
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF5B5F68), Color(0xFF15161A)), Offset(cx - r * 0.35f, cy - r * 0.35f), r * 1.2f),
        radius = r,
        center = Offset(cx, cy),
    )
    glowCircle(cx + r * 0.85f, cy - r * 0.95f, r * 0.35f, Color(0xFFFFD23A), r * 0.5f)
    drawCircle(Color.White, r * 0.16f, Offset(cx + r * 0.85f, cy - r * 0.95f))
}

/** Crack overlay for damaged multi-hit bricks; `level` 0 = light, 1 = heavy. */
private fun DrawScope.drawCrack(level: Int, w: Float, h: Float) {
    val p = Path()
    p.moveTo(w * 0.2f, 0f); p.lineTo(w * 0.31f, h * 0.45f); p.lineTo(w * 0.24f, h * 0.7f); p.lineTo(w * 0.34f, h)
    p.moveTo(w * 0.31f, h * 0.45f); p.lineTo(w * 0.5f, h * 0.38f); p.lineTo(w * 0.6f, h * 0.62f)
    if (level >= 1) {
        p.moveTo(w * 0.82f, 0f); p.lineTo(w * 0.71f, h * 0.35f); p.lineTo(w * 0.79f, h * 0.6f); p.lineTo(w * 0.67f, h)
        p.moveTo(w * 0.71f, h * 0.35f); p.lineTo(w * 0.55f, h * 0.2f)
        p.moveTo(w * 0.6f, h * 0.62f); p.lineTo(w * 0.72f, h * 0.8f)
    }
    drawPath(p, Color.Black.copy(alpha = 0.6f), style = Stroke(width = h * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    translate(0f, h * 0.045f) {
        drawPath(p, Color.White.copy(alpha = 0.35f), style = Stroke(width = h * 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Chrome sphere, plasma pearl (space) or fireball. */
private fun DrawScope.drawBall(style: BrickStyle, r: Float, fire: Boolean) {
    val c = center
    val hl = Offset(c.x - r * 0.35f, c.y - r * 0.4f)
    when {
        fire -> {
            glowCircle(c.x, c.y, r * 1.35f, Color(0xFFFF6A00).copy(alpha = 0.9f), r * 0.9f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White, Color(0xFFFFE38A), Color(0xFFFF8A1E), Color(0xFFD9380F)), hl, r * 1.4f),
                radius = r,
                center = c,
            )
        }
        style == BrickStyle.Neon -> {
            glowCircle(c.x, c.y, r * 1.3f, Color(0xFF00E5FF).copy(alpha = 0.85f), r * 0.9f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White, Color(0xFFB8F6FF), Color(0xFF3FD8F5)), hl, r * 1.5f),
                radius = r,
                center = c,
            )
        }
        else -> {
            glowCircle(c.x, c.y + r * 0.45f, r * 0.95f, Color.Black.copy(alpha = 0.55f), r * 0.45f)
            glowCircle(c.x, c.y, r * 1.1f, Color.White.copy(alpha = 0.3f), r * 0.7f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White, Color(0xFFD9DEE4), Color(0xFF8A929C), Color(0xFF3E444C)),
                    hl, r * 1.5f,
                ),
                radius = r,
                center = c,
            )
        }
    }
    drawCircle(Color.White.copy(alpha = 0.9f), r * 0.22f, hl)
}

/** Capsule paddle: body plus two coloured caps. */
private fun DrawScope.drawPaddle(style: BrickStyle, pad: Float, ph: Float, cap: Float) {
    val l = pad
    val t = pad
    val r = size.width - pad
    val b = pad + ph
    val body: List<Color>
    val caps: List<Color>
    when (style) {
        BrickStyle.Glossy -> {
            body = listOf(Color(0xFFFFFFFF), Color(0xFFE9ECEF), Color(0xFFB9C0C8), Color(0xFF8C949E))
            caps = listOf(Color(0xFFFF8A7A), Color(0xFFE53935), Color(0xFF9E1A1A))
        }
        BrickStyle.Sandstone -> {
            body = listOf(Color(0xFFFFF4C8), Color(0xFFF2C14E), Color(0xFFB07A1E), Color(0xFFE0A93A), Color(0xFF8A5A12))
            caps = listOf(Color(0xFF7FB2FF), Color(0xFF1F4FB8), Color(0xFF0B2466))
        }
        BrickStyle.Frost -> {
            body = listOf(Color(0xFFFFFFFF), Color(0xFFB8C4D0), Color(0xFF5C6874), Color(0xFFD7DEE6), Color(0xFF7A8591))
            caps = listOf(Color(0xFFD9FBFF), Color(0xFF4FD8F5), Color(0xFF13708F))
        }
        BrickStyle.Neon -> {
            body = listOf(Color(0xFF2A3160), Color(0xFF12163A), Color(0xFF0A0D26))
            caps = listOf(Color(0xFFFF8BE2), Color(0xFFFF2BD6), Color(0xFF9C0F84))
        }
    }
    if (style == BrickStyle.Neon) {
        glowRoundRect(l, t, r, b, ph / 2f, Color(0xFF00E5FF), pad * 0.6f, strokeWidth = ph * 0.2f)
    } else {
        glowRoundRect(l, t + ph * 0.3f, r, b + ph * 0.3f, ph / 2f, Color.Black.copy(alpha = 0.6f), pad * 0.6f)
    }
    val bodyBrush = Brush.verticalGradient(body, startY = t, endY = b)
    val capBrush = Brush.verticalGradient(caps, startY = t, endY = b)
    drawRoundRect(bodyBrush, Offset(l, t), Size(r - l, ph), CornerRadius(ph / 2f))
    clipRect(0f, 0f, l + cap, size.height) {
        drawRoundRect(capBrush, Offset(l, t), Size(r - l, ph), CornerRadius(ph / 2f))
    }
    clipRect(r - cap, 0f, size.width, size.height) {
        drawRoundRect(capBrush, Offset(l, t), Size(r - l, ph), CornerRadius(ph / 2f))
    }
    val seam = max(1f, ph * 0.07f)
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(l + cap, t + seam), Offset(l + cap, b - seam), strokeWidth = seam)
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(r - cap, t + seam), Offset(r - cap, b - seam), strokeWidth = seam)
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0f)),
            startY = t, endY = t + ph * 0.55f,
        ),
        topLeft = Offset(l + ph * 0.25f, t + ph * 0.1f),
        size = Size(r - l - ph * 0.5f, ph * 0.36f),
        cornerRadius = CornerRadius(ph * 0.18f),
    )
    if (style == BrickStyle.Neon) {
        drawRoundRect(Color(0xFF7DF3FF), Offset(l, t), Size(r - l, ph), CornerRadius(ph / 2f), style = Stroke(width = ph * 0.07f))
    }
}

/** Falling power-up capsule with its glyph. */
private fun DrawScope.drawCapsule(p: PowerUp, pad: Float, w: Float, h: Float, textMeasurer: TextMeasurer) {
    val col = BrickPalette.powerUp(p)
    glowRoundRect(pad, pad, pad + w, pad + h, h / 2f, col.copy(alpha = 0.85f), pad * 0.7f)
    if (p.curse) {
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF3A0A1C), BrickPalette.CurseBody), startY = pad, endY = pad + h),
            topLeft = Offset(pad, pad),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2f),
        )
        // Stripes
        clipRect(pad + h * 0.35f, pad, pad + w - h * 0.35f, pad + h) {
            var x = pad
            while (x < pad + w + h) {
                drawLine(col.copy(alpha = 0.22f), Offset(x, pad + h), Offset(x + h * 0.6f, pad), strokeWidth = h * 0.16f)
                x += h * 0.5f
            }
        }
        drawRoundRect(col, Offset(pad, pad), Size(w, h), CornerRadius(h / 2f), style = Stroke(width = max(1.5f, h * 0.09f)))
    } else {
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(light(col, 0.5f), col, dark(col, 0.35f)), startY = pad, endY = pad + h),
            topLeft = Offset(pad, pad),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2f),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)), startY = pad, endY = pad + h * 0.5f),
            topLeft = Offset(pad + h * 0.3f, pad + h * 0.08f),
            size = Size(w - h * 0.6f, h * 0.36f),
            cornerRadius = CornerRadius(h * 0.18f),
        )
        drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(pad, pad), Size(w, h), CornerRadius(h / 2f), style = Stroke(width = max(1f, h * 0.06f)))
    }
    val layout = textMeasurer.measure(
        text = p.glyph,
        style = TextStyle(
            color = if (p.curse) light(col, 0.25f) else Color.White,
            fontSize = (h * 0.62f).toSp(),
            fontWeight = FontWeight.Black,
        ),
        density = this,
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
        shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, h * 0.05f), h * 0.1f),
    )
}

/** Enemy drone. */
private fun DrawScope.drawDrone(r: Float) {
    val c = center
    val red = BrickPalette.DroneRed
    glowCircle(c.x, c.y, r * 1.05f, red.copy(alpha = 0.75f), r * 0.55f)
    for (k in 0 until 6) {
        val a = k * (PI / 3.0).toFloat()
        val ca = cos(a)
        val sa = sin(a)
        drawLine(
            Color(0xFF8C96A3),
            Offset(c.x + ca * r * 0.7f, c.y + sa * r * 0.7f),
            Offset(c.x + ca * r * 1.35f, c.y + sa * r * 1.35f),
            strokeWidth = r * 0.22f,
            cap = StrokeCap.Round,
        )
        drawCircle(red, r * 0.12f, Offset(c.x + ca * r * 1.35f, c.y + sa * r * 1.35f))
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF9AA4B1), Color(0xFF3A414B), Color(0xFF15181D)),
            Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.2f,
        ),
        radius = r * 0.85f,
        center = c,
    )
    drawCircle(Color(0xFF0B0C10), r * 0.42f, c)
    glowCircle(c.x, c.y, r * 0.3f, red, r * 0.25f)
    drawCircle(Color.White, r * 0.1f, Offset(c.x - r * 0.08f, c.y - r * 0.08f))
    drawCircle(red, r * 0.85f, c, style = Stroke(width = r * 0.08f))
}

// Backgrounds

private fun bakeBackground(theme: BrickTheme, width: Int, height: Int, scale: Float, density: Density): ImageBitmap =
    bake(width, height, density) {
        when (theme) {
            BrickTheme.ForestDawn -> drawForestBackground(scale, ForestDawnLook)
            BrickTheme.ForestDusk -> drawForestBackground(scale, ForestDuskLook)
            BrickTheme.ForestNight -> drawForestBackground(scale, ForestNightLook)
            BrickTheme.Glacier -> drawGlacierBackground(scale)
            BrickTheme.Jungle -> drawCanopyBackground(scale)
            BrickTheme.Dunes -> drawDunesBackground(scale)
            BrickTheme.Canyon -> drawCanyonBackground(scale)
            BrickTheme.Orbit -> drawOrbitBackground(scale)
            BrickTheme.Nebula -> drawNebulaBackground(scale)
            BrickTheme.Void -> drawVoidBackground(scale)
        }
    }

/** Jungle background. */
private fun DrawScope.drawCanopyBackground(scale: Float) {
    val w = size.width
    val h = size.height
    val rnd = Random(42)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0D2412), Color(0xFF1C4520), Color(0xFF16391A), Color(0xFF08170A))))
    drawRect(Brush.radialGradient(listOf(Color(0x46C8FF9A), Color.Transparent), Offset(w * 0.5f, h * 0.02f), h * 0.75f))
    repeat(42) {
        val r = (0.02f + rnd.nextFloat() * 0.07f) * w
        glowCircle(
            rnd.nextFloat() * w, rnd.nextFloat() * h * 0.85f, r,
            Color(0xFFD2FF9A).copy(alpha = 0.03f + rnd.nextFloat() * 0.08f), r * 0.35f,
        )
    }
    // Vines
    for (side in 0..1) {
        val x0 = if (side == 0) w * 0.04f else w * 0.96f
        var y = 0f
        var x = x0
        while (y < h * 0.28f) {
            val leaf = (10f + rnd.nextFloat() * 10f) * scale
            val col = lerp(Color(0xFF123D16), Color(0xFF5DB343), rnd.nextFloat() * 0.7f)
            rotate(degrees = rnd.nextFloat() * 120f - 60f, pivot = Offset(x, y)) {
                drawOval(col, Offset(x - leaf, y - leaf * 0.4f), Size(leaf * 2f, leaf * 0.8f))
            }
            y += leaf * 0.9f
            x = x0 + (rnd.nextFloat() - 0.5f) * 24f * scale
        }
    }
    // Leaves on the floor
    repeat(170) {
        val x = rnd.nextFloat() * w
        val y = h - rnd.nextFloat() * h * 0.07f
        val lw = (0.018f + rnd.nextFloat() * 0.03f) * w
        val col = lerp(Color(0xFF0B2E0E), Color(0xFF63C24A), rnd.nextFloat() * 0.8f)
        rotate(degrees = rnd.nextFloat() * 180f, pivot = Offset(x, y)) {
            drawOval(col, Offset(x - lw, y - lw * 0.42f), Size(lw * 2f, lw * 0.84f))
        }
    }
    drawVignette()
}

/** Colours for one time of day in the forest. */
private class ForestLook(
    val sky: Array<Pair<Float, Color>>,
    val light: Color,
    val glow: Color,
    val lightX: Float,
    val lightY: Float,
    val lightR: Float,
    val layers: Array<Color>,
    val mist: Color,
    val broadleaf: Boolean,
    val night: Boolean,
)

private val ForestDawnLook = ForestLook(
    sky = arrayOf(0f to Color(0xFF16213C), 0.3f to Color(0xFF384B75), 0.5f to Color(0xFFB98AA0), 0.62f to Color(0xFFFFD3A0)),
    light = Color(0xFFFFF4D2), glow = Color(0xFFFFC98A), lightX = 0.7f, lightY = 0.6f, lightR = 0.07f,
    layers = arrayOf(Color(0xFF8AA3AE), Color(0xFF557A6A), Color(0xFF2A4C34), Color(0xFF112417)),
    mist = Color(0xFFFFEBD8), broadleaf = false, night = false,
)

private val ForestDuskLook = ForestLook(
    sky = arrayOf(0f to Color(0xFF1A0A24), 0.28f to Color(0xFF461838), 0.46f to Color(0xFFA43E2E), 0.6f to Color(0xFFF2913A)),
    light = Color(0xFFFFD98A), glow = Color(0xFFFF8A3A), lightX = 0.3f, lightY = 0.62f, lightR = 0.1f,
    layers = arrayOf(Color(0xFF6A2E3E), Color(0xFF8C3A22), Color(0xFF4A1A10), Color(0xFF1E0A06)),
    mist = Color(0xFFFFB070), broadleaf = true, night = false,
)

private val ForestNightLook = ForestLook(
    sky = arrayOf(0f to Color(0xFF02040A), 0.35f to Color(0xFF07122A), 0.62f to Color(0xFF12284A)),
    light = Color(0xFFEAF4FF), glow = Color(0xFF8FC8FF), lightX = 0.76f, lightY = 0.13f, lightR = 0.065f,
    layers = arrayOf(Color(0xFF1A2C48), Color(0xFF0F1C33), Color(0xFF08111F), Color(0xFF03070F)),
    mist = Color(0xFF6A9AD0), broadleaf = false, night = true,
)

/** Forest background (dawn, dusk or night). */
private fun DrawScope.drawForestBackground(scale: Float, look: ForestLook) {
    val w = size.width
    val h = size.height
    val rnd = Random(if (look.night) 5 else if (look.broadleaf) 9 else 3)
    drawRect(Brush.verticalGradient(*look.sky, startY = 0f, endY = h * 0.65f))
    if (look.night) drawStars(rnd, 220, h * 0.6f, scale)

    val lx = w * look.lightX
    val ly = h * look.lightY
    val lr = w * look.lightR
    drawCircle(Brush.radialGradient(listOf(look.glow.copy(alpha = 0.55f), Color.Transparent), Offset(lx, ly), lr * 7f), lr * 7f, Offset(lx, ly))
    glowCircle(lx, ly, lr * 1.1f, look.glow, lr * 0.6f)
    drawCircle(look.light, lr, Offset(lx, ly))
    if (look.night) {
        repeat(5) {
            val a = rnd.nextFloat() * 6.28f
            val d = rnd.nextFloat() * lr * 0.6f
            drawCircle(Color(0xFFB8C6D8), lr * (0.1f + rnd.nextFloat() * 0.14f), Offset(lx + cos(a) * d, ly + sin(a) * d), alpha = 0.6f)
        }
    } else if (!look.broadleaf) {
        // Light rays at dawn
        for (k in 0 until 5) {
            val x0 = w * (0.45f + k * 0.14f)
            val shaft = Path()
            shaft.moveTo(x0, 0f)
            shaft.lineTo(x0 + w * 0.06f, 0f)
            shaft.lineTo(x0 - w * 0.25f, h * 0.75f)
            shaft.lineTo(x0 - w * 0.36f, h * 0.75f)
            shaft.close()
            drawPath(shaft, Brush.verticalGradient(listOf(Color(0x22FFF2D0), Color.Transparent), startY = 0f, endY = h * 0.75f))
        }
    }

    val bases = floatArrayOf(0.6f, 0.68f, 0.8f, 0.95f)
    val heights = floatArrayOf(0.1f, 0.15f, 0.22f, 0.3f)
    for (layer in look.layers.indices) {
        val base = h * bases[layer]
        val trees = Path()
        var x = -w * 0.05f
        while (x < w * 1.05f) {
            val th = h * heights[layer] * (0.7f + rnd.nextFloat() * 0.5f)
            val tw = th * (if (look.broadleaf) 0.55f else 0.38f)
            if (look.broadleaf) addBroadleaf(trees, x, base, th, tw) else addPine(trees, x, base, th, tw)
            x += tw * (0.55f + rnd.nextFloat() * 0.5f)
        }
        trees.addRect(Rect(0f, base - 2f, w, h))
        drawPath(trees, look.layers[layer])
        if (layer < 3) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, look.mist.copy(alpha = 0.16f), Color.Transparent), startY = base - h * 0.06f, endY = base + h * 0.03f),
                Offset(0f, base - h * 0.06f), Size(w, h * 0.09f),
            )
        }
    }
    if (look.night) {
        // Mushrooms
        repeat(14) {
            val mx = rnd.nextFloat() * w
            val my = h * (0.9f + rnd.nextFloat() * 0.08f)
            val mr = (4f + rnd.nextFloat() * 5f) * scale
            glowCircle(mx, my, mr * 1.6f, Color(0xFF4FF0FF).copy(alpha = 0.6f), mr * 1.2f)
            drawOval(Color(0xFF9FF8FF), Offset(mx - mr, my - mr * 0.5f), Size(mr * 2f, mr))
        }
    }
    drawVignette()
}

private fun addPine(path: Path, x: Float, base: Float, h: Float, w: Float) {
    path.addRect(Rect(x - w * 0.05f, base - h * 0.2f, x + w * 0.05f, base))
    for (k in 0 until 4) {
        val top = base - h + k * h * 0.2f
        val bottom = top + h * 0.36f
        val half = w * (0.22f + 0.1f * k)
        path.moveTo(x, top)
        path.lineTo(x + half, bottom)
        path.lineTo(x - half, bottom)
        path.close()
    }
}

private fun addBroadleaf(path: Path, x: Float, base: Float, h: Float, w: Float) {
    path.addRect(Rect(x - w * 0.06f, base - h * 0.45f, x + w * 0.06f, base))
    val cy = base - h * 0.62f
    val r = w * 0.32f
    path.addOval(Rect(x - r, cy - r, x + r, cy + r))
    path.addOval(Rect(x - r * 1.6f, cy - r * 0.3f, x - r * 0.1f, cy + r * 0.9f))
    path.addOval(Rect(x + r * 0.1f, cy - r * 0.4f, x + r * 1.6f, cy + r * 0.9f))
    path.addOval(Rect(x - r * 0.8f, cy - r * 1.35f, x + r * 0.8f, cy - r * 0.1f))
}

/** Glacier background. */
private fun DrawScope.drawGlacierBackground(scale: Float) {
    val w = size.width
    val h = size.height
    val rnd = Random(17)
    drawRect(Brush.verticalGradient(0f to Color(0xFF030A18), 0.3f to Color(0xFF0A2340), 0.55f to Color(0xFF1C5380), 0.66f to Color(0xFF6FB4D8)))
    drawStars(rnd, 160, h * 0.5f, scale)
    val aurora = arrayOf(Color(0xFF3BFFB0), Color(0xFF2EE6C9), Color(0xFF9A6BFF))
    for (k in aurora.indices) {
        val band = Path()
        val y0 = h * (0.1f + k * 0.07f)
        var x = -w * 0.1f
        band.moveTo(x, y0)
        while (x < w * 1.1f) {
            x += w * 0.05f
            band.lineTo(x, y0 + sin(x / w * 7f + k * 1.7f) * h * 0.035f)
        }
        glowPath(band, aurora[k].copy(alpha = 0.32f - k * 0.05f), w * 0.05f, strokeWidth = w * 0.09f)
        glowPath(band, aurora[k].copy(alpha = 0.5f), w * 0.01f, strokeWidth = w * 0.008f)
    }
    val bases = floatArrayOf(0.64f, 0.74f, 0.86f)
    val colors = arrayOf(Color(0xFF9CC4E0), Color(0xFF5A88B4), Color(0xFF24476E))
    for (layer in 0..2) {
        val base = h * bases[layer]
        val ridge = Path()
        val caps = Path()
        ridge.moveTo(0f, h)
        var x = 0f
        var y = base - rnd.nextFloat() * h * 0.08f
        ridge.lineTo(x, y)
        while (x < w) {
            val step = w * (0.1f + rnd.nextFloat() * 0.12f)
            val peakX = x + step * 0.5f
            val peakY = base - h * (0.1f + rnd.nextFloat() * 0.12f) * (1f + layer * 0.25f)
            val endY = base - rnd.nextFloat() * h * 0.05f
            ridge.lineTo(peakX, peakY)
            ridge.lineTo(x + step, endY)
            // Snow cap
            val capL = Offset(peakX + (x - peakX) * 0.32f, peakY + (y - peakY) * 0.32f)
            val capR = Offset(peakX + (x + step - peakX) * 0.32f, peakY + (endY - peakY) * 0.32f)
            caps.moveTo(peakX, peakY)
            caps.lineTo(capR.x, capR.y)
            caps.lineTo((peakX + capR.x) / 2f, capR.y - h * 0.008f)
            caps.lineTo(peakX, capR.y + h * 0.01f)
            caps.lineTo((peakX + capL.x) / 2f, capL.y - h * 0.006f)
            caps.lineTo(capL.x, capL.y)
            caps.close()
            x += step
            y = endY
        }
        ridge.lineTo(w, h)
        ridge.close()
        drawPath(ridge, colors[layer])
        drawPath(caps, Color.White, alpha = 0.85f - layer * 0.15f)
    }
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFFBFF1FF), Color(0xFF3F86B0)), startY = h * 0.93f, endY = h),
        Offset(0f, h * 0.93f), Size(w, h * 0.07f),
    )
    drawVignette()
}

/** Desert background. */
private fun DrawScope.drawDunesBackground(scale: Float) {
    val w = size.width
    val h = size.height
    val rnd = Random(23)
    drawRect(Brush.verticalGradient(0f to Color(0xFF240B05), 0.25f to Color(0xFF64220E), 0.45f to Color(0xFFBA531C), 0.6f to Color(0xFFF2A341), 0.67f to Color(0xFFFFD98A)))
    val sx = w * 0.5f
    val sy = h * 0.5f
    val sr = w * 0.12f
    drawCircle(Brush.radialGradient(listOf(Color(0x99FFC060), Color.Transparent), Offset(sx, sy), sr * 6f), sr * 6f, Offset(sx, sy))
    glowCircle(sx, sy, sr * 1.1f, Color(0xFFFFE6A0), sr * 0.5f)
    drawCircle(Color(0xFFFFF6D8), sr, Offset(sx, sy))
    val pb = h * 0.67f
    drawPyramid(w * 0.3f, pb, w * 0.4f, h * 0.17f)
    drawPyramid(w * 0.74f, pb, w * 0.24f, h * 0.1f)
    val bases = floatArrayOf(0.68f, 0.76f, 0.86f, 0.95f)
    val colors = arrayOf(Color(0xFFE0A050), Color(0xFFC27A35), Color(0xFF8F4F20), Color(0xFF5A2C10))
    for (layer in 0..3) {
        val base = h * bases[layer]
        val dune = Path()
        dune.moveTo(0f, h)
        dune.lineTo(0f, base)
        var x = 0f
        while (x < w) {
            val step = w * (0.3f + rnd.nextFloat() * 0.25f)
            val crest = base - h * (0.02f + rnd.nextFloat() * 0.04f)
            dune.cubicTo(x + step * 0.3f, crest, x + step * 0.6f, crest, x + step, base + (rnd.nextFloat() - 0.5f) * h * 0.02f)
            x += step
        }
        dune.lineTo(w, h)
        dune.close()
        drawPath(dune, colors[layer])
        drawPath(dune, Color(0xFFFFE0A0), alpha = 0.18f, style = Stroke(width = 1.5f * scale))
    }
    repeat(600) {
        drawRect(Color(0xFFFFE6B0), Offset(rnd.nextFloat() * w, h * 0.68f + rnd.nextFloat() * h * 0.32f), Size(1.5f, 1.5f), alpha = rnd.nextFloat() * 0.25f)
    }
    drawVignette()
}

private fun DrawScope.drawPyramid(cx: Float, base: Float, width: Float, height: Float) {
    val lit = Path()
    lit.moveTo(cx, base - height)
    lit.lineTo(cx - width / 2f, base)
    lit.lineTo(cx + width * 0.12f, base)
    lit.close()
    val shade = Path()
    shade.moveTo(cx, base - height)
    shade.lineTo(cx + width * 0.12f, base)
    shade.lineTo(cx + width / 2f, base)
    shade.close()
    drawPath(lit, Color(0xFFD08A45))
    drawPath(shade, Color(0xFF7A3E16))
    // Stone rows
    var y = base - height * 0.85f
    while (y < base) {
        val f = (y - (base - height)) / height
        drawLine(Color(0xFF5A2C10), Offset(cx - width / 2f * f, y), Offset(cx + width / 2f * f, y), strokeWidth = 1.2f, alpha = 0.35f)
        y += height * 0.09f
    }
}

/** Desert night background. */
private fun DrawScope.drawCanyonBackground(scale: Float) {
    val w = size.width
    val h = size.height
    val rnd = Random(31)
    drawRect(Brush.verticalGradient(0f to Color(0xFF05040F), 0.35f to Color(0xFF180B2E), 0.56f to Color(0xFF441846), 0.66f to Color(0xFF8A3A50)))
    val band = Path()
    band.moveTo(-w * 0.1f, h * 0.45f)
    band.cubicTo(w * 0.3f, h * 0.3f, w * 0.6f, h * 0.15f, w * 1.1f, -h * 0.02f)
    glowPath(band, Color(0xFFD9C2FF).copy(alpha = 0.16f), w * 0.08f, strokeWidth = w * 0.18f)
    glowPath(band, Color(0xFFFFE6F0).copy(alpha = 0.12f), w * 0.03f, strokeWidth = w * 0.05f)
    drawStars(rnd, 320, h * 0.62f, scale)
    val mx = w * 0.22f
    val my = h * 0.15f
    val mr = w * 0.055f
    glowCircle(mx, my, mr * 1.2f, Color(0xFFFFE9C0).copy(alpha = 0.6f), mr)
    drawCircle(Color(0xFFFFF2D8), mr, Offset(mx, my))
    drawCircle(Color(0xFF0B0820), mr * 0.92f, Offset(mx + mr * 0.42f, my - mr * 0.12f))
    val layers = arrayOf(Color(0xFF3A1630), Color(0xFF22091A))
    val bases = floatArrayOf(0.66f, 0.78f)
    for (layer in 0..1) {
        val base = h * bases[layer]
        val mesa = Path()
        mesa.moveTo(0f, h)
        var x = 0f
        mesa.lineTo(0f, base)
        while (x < w) {
            val gapW = w * (0.05f + rnd.nextFloat() * 0.1f)
            val topW = w * (0.12f + rnd.nextFloat() * 0.18f)
            val top = base - h * (0.06f + rnd.nextFloat() * 0.07f) * (1f + layer * 0.4f)
            mesa.lineTo(x + gapW, base)
            mesa.lineTo(x + gapW + topW * 0.12f, top + h * 0.012f)
            mesa.lineTo(x + gapW + topW * 0.18f, top)
            mesa.lineTo(x + gapW + topW * 0.82f, top)
            mesa.lineTo(x + gapW + topW * 0.9f, top + h * 0.02f)
            mesa.lineTo(x + gapW + topW, base)
            x += gapW + topW
        }
        mesa.lineTo(w, base)
        mesa.lineTo(w, h)
        mesa.close()
        drawPath(mesa, layers[layer])
        drawPath(mesa, Color(0xFFFF8A5C), alpha = 0.22f, style = Stroke(width = 2f * scale))
    }
    for (k in 0 until 4) {
        drawSaguaro(w * (0.1f + k * 0.27f + rnd.nextFloat() * 0.05f), h * (0.93f + rnd.nextFloat() * 0.04f), h * (0.07f + rnd.nextFloat() * 0.05f))
    }
    drawVignette()
}

private fun DrawScope.drawSaguaro(x: Float, base: Float, height: Float) {
    val c = Color(0xFF12050C)
    val t = height * 0.16f
    drawRoundRect(c, Offset(x - t / 2f, base - height), Size(t, height), CornerRadius(t / 2f))
    drawRoundRect(c, Offset(x - t * 2.2f, base - height * 0.6f), Size(t * 0.8f, height * 0.32f), CornerRadius(t / 2f))
    drawRoundRect(c, Offset(x - t * 2.2f, base - height * 0.32f), Size(t * 2f, t * 0.8f), CornerRadius(t / 2f))
    drawRoundRect(c, Offset(x + t * 1.4f, base - height * 0.75f), Size(t * 0.8f, height * 0.36f), CornerRadius(t / 2f))
    drawRoundRect(c, Offset(x, base - height * 0.43f), Size(t * 2.2f, t * 0.8f), CornerRadius(t / 2f))
}

/** Orbit background. */
private fun DrawScope.drawOrbitBackground(scale: Float) {
    val w = size.width
    val h = size.height
    val rnd = Random(47)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF01030A), Color(0xFF040A1E), Color(0xFF061232))))
    drawStars(rnd, 380, h, scale)
    val pr = w * 1.25f
    val pc = Offset(w * 0.5f, h * 0.8f + pr)
    val rim = Path()
    rim.addOval(Rect(pc.x - pr, pc.y - pr, pc.x + pr, pc.y + pr))
    glowPath(rim, Color(0xFF4FC3FF).copy(alpha = 0.7f), w * 0.04f, strokeWidth = w * 0.03f)
    drawCircle(
        Brush.verticalGradient(listOf(Color(0xFF5AB0FF), Color(0xFF1E5FC0), Color(0xFF0A2A6A), Color(0xFF020818)), startY = pc.y - pr, endY = pc.y - pr + h * 0.22f),
        pr, pc,
    )
    // Clouds
    repeat(16) {
        val a = (-90f + (rnd.nextFloat() - 0.5f) * 40f) * (PI.toFloat() / 180f)
        val d = pr * (0.97f - rnd.nextFloat() * 0.06f)
        val cx = pc.x + cos(a) * d
        val cy = pc.y + sin(a) * d
        val cw = w * (0.08f + rnd.nextFloat() * 0.14f)
        rotate((a * 180f / PI.toFloat()) + 90f, Offset(cx, cy)) {
            drawOval(Color.White, Offset(cx - cw, cy - cw * 0.08f), Size(cw * 2f, cw * 0.16f), alpha = 0.18f)
        }
    }
    drawCircle(Color(0xFFBFEAFF), pr, pc, alpha = 0.9f, style = Stroke(width = 2f * scale))
    val mx = w * 0.84f
    val my = h * 0.1f
    val mr = w * 0.05f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFE8E4DC), Color(0xFF8C8680), Color(0xFF2A2826)), Offset(mx - mr * 0.4f, my - mr * 0.4f), mr * 1.6f),
        mr, Offset(mx, my),
    )
    drawVignette()
}

/** Nebula background. */
private fun DrawScope.drawNebulaBackground(scale: Float) {
    val w = size.width
    val h = size.height
    val rnd = Random(53)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF05020A), Color(0xFF0E0420), Color(0xFF07020F))))
    val gas = arrayOf(Color(0xFFFF2E9A), Color(0xFF7A2EFF), Color(0xFF2EC8FF), Color(0xFFFF6A3A))
    repeat(34) {
        val t = rnd.nextFloat()
        val cx = t * w
        val cy = h * (0.15f + t * 0.45f) + (rnd.nextFloat() - 0.5f) * h * 0.2f
        val r = w * (0.08f + rnd.nextFloat() * 0.22f)
        glowCircle(cx, cy, r, gas[rnd.nextInt(gas.size)].copy(alpha = 0.06f + rnd.nextFloat() * 0.1f), r * 0.6f)
    }
    drawStars(rnd, 300, h, scale)
    repeat(10) {
        val cx = rnd.nextFloat() * w
        val cy = h * (0.35f + rnd.nextFloat() * 0.6f)
        val r = w * (0.015f + rnd.nextFloat() * 0.045f)
        val rock = Path()
        for (v in 0 until 9) {
            val a = v * (2f * PI.toFloat() / 9f)
            val rr = r * (0.7f + rnd.nextFloat() * 0.4f)
            if (v == 0) rock.moveTo(cx + cos(a) * rr, cy + sin(a) * rr) else rock.lineTo(cx + cos(a) * rr, cy + sin(a) * rr)
        }
        rock.close()
        drawPath(rock, Brush.radialGradient(listOf(Color(0xFF7A6660), Color(0xFF3A2C2A), Color(0xFF140E0E)), Offset(cx - r * 0.4f, cy - r * 0.4f), r * 1.6f))
        drawPath(rock, Color(0xFFFF8AC8), alpha = 0.35f, style = Stroke(width = 1.5f * scale))
        drawCircle(Color(0xFF140E0E), r * 0.18f, Offset(cx + r * 0.2f, cy + r * 0.1f), alpha = 0.6f)
    }
    drawVignette()
}

/** Black hole background. */
private fun DrawScope.drawVoidBackground(scale: Float) {
    val w = size.width
    val h = size.height
    val rnd = Random(61)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF07030F), Color(0xFF0C0418))))
    drawStars(rnd, 260, h, scale)
    val c = Offset(w * 0.5f, 250f * scale)
    // Grid bent towards the centre
    val grid = Path()
    val pull = w * 0.35f
    var gy = -h * 0.1f
    while (gy < h) {
        var x = 0f
        var first = true
        while (x <= w) {
            val dx = x - c.x
            val dy = gy - c.y
            val f = exp(-(dx * dx + dy * dy) / (pull * pull))
            val py = gy + (c.y - gy) * f * 0.6f
            val px = x + (c.x - x) * f * 0.2f
            if (first) grid.moveTo(px, py) else grid.lineTo(px, py)
            first = false
            x += w / 40f
        }
        gy += h / 22f
    }
    var gx = 0f
    while (gx <= w) {
        var y = 0f
        var first = true
        while (y <= h) {
            val dx = gx - c.x
            val dy = y - c.y
            val f = exp(-(dx * dx + dy * dy) / (pull * pull))
            val px = gx + (c.x - gx) * f * 0.6f
            val py = y + (c.y - y) * f * 0.2f
            if (first) grid.moveTo(px, py) else grid.lineTo(px, py)
            first = false
            y += h / 60f
        }
        gx += w / 12f
    }
    drawPath(grid, Color(0xFF7A3AD0), alpha = 0.2f, style = Stroke(width = 1.2f * scale))
    for (k in 1..6) {
        drawCircle(Color(0xFFC77DFF), w * (0.18f + k * 0.08f), c, alpha = 0.1f - k * 0.012f, style = Stroke(width = 2f * scale))
    }
    val disk = Path()
    disk.addOval(Rect(c.x - w * 0.46f, c.y - w * 0.08f, c.x + w * 0.46f, c.y + w * 0.08f))
    glowPath(disk, Color(0xFFFF7A3A).copy(alpha = 0.35f), w * 0.05f, strokeWidth = w * 0.05f)
    glowPath(disk, Color(0xFFFFD08A).copy(alpha = 0.3f), w * 0.01f, strokeWidth = w * 0.01f)
    drawCircle(Brush.radialGradient(listOf(Color(0x66B04BFF), Color.Transparent), c, w * 0.6f), w * 0.6f, c)
    drawVignette()
}

/** Random stars, a few of them glowing. */
private fun DrawScope.drawStars(rnd: Random, count: Int, maxY: Float, scale: Float) {
    repeat(count) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * maxY
        val bright = rnd.nextFloat()
        if (bright > 0.96f) {
            glowCircle(x, y, 2.4f * scale, Color(0xFFD8E8FF), 4f * scale)
            drawCircle(Color.White, 1.6f * scale, Offset(x, y))
        } else {
            drawCircle(Color.White, (0.5f + bright) * scale, Offset(x, y), alpha = 0.25f + bright * 0.6f)
        }
    }
}

private fun DrawScope.drawVignette() {
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xA0000000)), Offset(size.width / 2f, size.height * 0.45f), size.height * 0.8f))
}
