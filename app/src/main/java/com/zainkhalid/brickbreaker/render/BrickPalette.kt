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

import androidx.compose.ui.graphics.Color
import com.zainkhalid.brickbreaker.engine.BrickKind
import com.zainkhalid.brickbreaker.engine.BrickTheme
import com.zainkhalid.brickbreaker.engine.PART_COLOR_COUNT
import com.zainkhalid.brickbreaker.engine.PART_CURSE
import com.zainkhalid.brickbreaker.engine.PART_DRONE
import com.zainkhalid.brickbreaker.engine.PART_FIRE
import com.zainkhalid.brickbreaker.engine.PART_GOLD
import com.zainkhalid.brickbreaker.engine.PART_LEAF
import com.zainkhalid.brickbreaker.engine.PART_PLASMA
import com.zainkhalid.brickbreaker.engine.PART_SAND
import com.zainkhalid.brickbreaker.engine.PART_SPARK
import com.zainkhalid.brickbreaker.engine.PART_TEAL
import com.zainkhalid.brickbreaker.engine.PART_VOID
import com.zainkhalid.brickbreaker.engine.PowerUp
import com.zainkhalid.brickbreaker.engine.SHOT_ANKH
import com.zainkhalid.brickbreaker.engine.SHOT_KIND_COUNT
import com.zainkhalid.brickbreaker.engine.SHOT_PLASMA
import com.zainkhalid.brickbreaker.engine.SHOT_SAND
import com.zainkhalid.brickbreaker.engine.SHOT_SEED
import com.zainkhalid.brickbreaker.engine.SHOT_STAR

/** Every colour the game uses, in one place. */
object BrickPalette {

    val Night = Color(0xFF07080D)
    val Ink = Color(0xFF0B1030)
    val White = Color(0xFFFFFFFF)
    val DroneRed = Color(0xFFFF2B6B)
    val CurseBody = Color(0xFF1A0610)

    /** Base colour per [BrickKind]. */
    val brick: Array<Color> = arrayOf(
        Color(0xFF1E88FF), // blue
        Color(0xFF52D12A), // green
        Color(0xFFFF8A1E), // orange
        Color(0xFFFF5CB6), // pink
        Color(0xFF9A3BFF), // violet
        Color(0xFFE8303A), // red
        Color(0xFF30D6F2), // cyan
        Color(0xFFFFD132), // yellow
        Color(0xFFC7D0DA), // silver
        Color(0xFFFFB81C), // gold
        Color(0xFF5B6573), // steel
        Color(0xFFFF4A1A), // tnt
    )

    /** Particle colour per slot: brick kinds first, then spark/fire/gold/laser. */
    val particle: Array<Color> = Array(PART_COLOR_COUNT) { i ->
        when {
            i < BrickKind.COUNT -> brick[i]
            i == PART_SPARK -> Color(0xFFFFFFFF)
            i == PART_FIRE -> Color(0xFFFF8C2A)
            i == PART_GOLD -> Color(0xFFFFE27A)
            i == PART_DRONE -> DroneRed
            i == PART_CURSE -> Color(0xFFB44BFF)
            i == PART_LEAF -> Color(0xFF7BE04A)
            i == PART_SAND -> Color(0xFFFFB347)
            i == PART_TEAL -> Color(0xFF2EE6C9)
            i == PART_PLASMA -> Color(0xFFFF3B8A)
            i == PART_VOID -> Color(0xFFB04BFF)
            else -> Color(0xFFFF3B5C)
        }
    }

    /** Glow colour per boss projectile kind. */
    val shot: Array<Color> = Array(SHOT_KIND_COUNT) { k ->
        when (k) {
            SHOT_SEED -> Color(0xFF9BE15D)
            SHOT_SAND -> Color(0xFFFF9A2E)
            SHOT_ANKH -> Color(0xFFFFD23A)
            SHOT_PLASMA -> Color(0xFFFF2E6E)
            SHOT_STAR -> Color(0xFFFFF3A0)
            else -> Color(0xFFB04BFF)
        }
    }

    /** Capsule colour per power-up. */
    fun powerUp(p: PowerUp): Color = when (p) {
        PowerUp.Expand -> Color(0xFF2EB8FF)
        PowerUp.Multi -> Color(0xFFFFC928)
        PowerUp.Slow -> Color(0xFF8A63FF)
        PowerUp.Laser -> Color(0xFFFF3B5C)
        PowerUp.Fire -> Color(0xFFFF7A1A)
        PowerUp.Catch -> Color(0xFF34D873)
        PowerUp.Shield -> Color(0xFF7FE8FF)
        PowerUp.Life -> Color(0xFFFF5CB6)
        PowerUp.Double -> Color(0xFF00E6A8)
        PowerUp.Blast -> Color(0xFFB6FF2E)
        PowerUp.Shrink -> Color(0xFFFF3355)
        PowerUp.Rush -> Color(0xFFFF6A1A)
        PowerUp.Reverse -> Color(0xFFB44BFF)
    }

    /** Primary accent per theme (HUD digits, shield, trails). */
    fun accent(theme: BrickTheme): Color = when (theme) {
        BrickTheme.ForestDawn -> Color(0xFFFFE08A)
        BrickTheme.ForestDusk -> Color(0xFFFFB347)
        BrickTheme.ForestNight -> Color(0xFFB8F0FF)
        BrickTheme.Glacier -> Color(0xFF9FF3FF)
        BrickTheme.Jungle -> Color(0xFFFFD54A)
        BrickTheme.Dunes -> Color(0xFFFFC94A)
        BrickTheme.Canyon -> Color(0xFFFF8A5C)
        BrickTheme.Orbit -> Color(0xFF7DF3FF)
        BrickTheme.Nebula -> Color(0xFFFF5CD6)
        BrickTheme.Void -> Color(0xFFC77DFF)
    }

    /** Secondary accent per theme. */
    fun accent2(theme: BrickTheme): Color = when (theme) {
        BrickTheme.ForestDawn -> Color(0xFF9CFF6B)
        BrickTheme.ForestDusk -> Color(0xFFFF6F61)
        BrickTheme.ForestNight -> Color(0xFF7CFFB2)
        BrickTheme.Glacier -> Color(0xFF8FB4FF)
        BrickTheme.Jungle -> Color(0xFF9CFF6B)
        BrickTheme.Dunes -> Color(0xFFFF7A2E)
        BrickTheme.Canyon -> Color(0xFFC9A2FF)
        BrickTheme.Orbit -> Color(0xFF4F8BFF)
        BrickTheme.Nebula -> Color(0xFF7DF3FF)
        BrickTheme.Void -> Color(0xFFFF4E6B)
    }

    /** HUD bar background per theme. */
    fun hud(theme: BrickTheme): Color = when (theme) {
        BrickTheme.ForestDawn -> Color(0xFF0E1E10)
        BrickTheme.ForestDusk -> Color(0xFF1E0F08)
        BrickTheme.ForestNight -> Color(0xFF050B14)
        BrickTheme.Glacier -> Color(0xFF06121F)
        BrickTheme.Jungle -> Color(0xFF0A1A0C)
        BrickTheme.Dunes -> Color(0xFF221306)
        BrickTheme.Canyon -> Color(0xFF140A1A)
        BrickTheme.Orbit -> Color(0xFF040814)
        BrickTheme.Nebula -> Color(0xFF12041A)
        BrickTheme.Void -> Color(0xFF08040F)
    }
}
