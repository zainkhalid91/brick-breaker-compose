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

/** Capsules dropped by bricks and drones. The last three are curses. */
enum class PowerUp(
    val glyph: String,
    val title: String,
    val hint: String,
    val duration: Float,
    val weight: Int,
    val curse: Boolean = false,
) {
    Expand("W", "WIDE PADDLE", "Wider paddle", 15f, 18),
    Multi("M", "MULTI BALL", "Split every ball in three", 0f, 16),
    Slow("S", "SLOW MOTION", "Balls slow down", 10f, 13),
    Laser("L", "LASER CANNONS", "Paddle fires lasers", 10f, 12),
    Fire("F", "FIREBALL", "Ball melts through bricks", 8f, 9),
    Catch("C", "MAGNET CATCH", "Paddle catches the ball", 12f, 11),
    Shield("B", "BARRIER", "Saves one lost ball", 0f, 10),
    Life("+1", "EXTRA LIFE", "One more life", 0f, 4),
    Double("x2", "DOUBLE SCORE", "All points doubled", 12f, 10),
    Blast("X", "BLAST BALL", "Next 3 hits explode", 0f, 8),
    Shrink("><", "SHRINK", "Paddle shrinks", 10f, 1, curse = true),
    Rush(">>", "RUSH", "Balls speed up", 8f, 1, curse = true),
    Reverse("<>", "REVERSED", "Controls flip", 6f, 1, curse = true),
}
