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

/** How bricks, ball and paddle are painted. */
enum class BrickStyle {
    /** Glossy candy bricks and a chrome ball. */
    Glossy,

    /** Carved, bevelled sandstone blocks. */
    Sandstone,

    /** Translucent, faceted ice. */
    Frost,

    /** Outlined neon tubes and a plasma ball. */
    Neon,
}

/** Level world: background, weather and colours. */
enum class BrickTheme(val style: BrickStyle, val region: String) {
    /** Morning forest: pale gold sky, misty pines, drifting pollen. */
    ForestDawn(BrickStyle.Glossy, "WHISPERING WOODS"),

    /** Autumn forest at sunset: amber light and falling leaves. */
    ForestDusk(BrickStyle.Glossy, "AMBER HOLLOW"),

    /** The same forest at midnight: moon, fog and fireflies. */
    ForestNight(BrickStyle.Glossy, "MIDNIGHT GROVE"),

    /** Ice peaks under an aurora, with snowfall. */
    Glacier(BrickStyle.Frost, "FROSTFANG PEAKS"),

    /** Deep jungle with hanging vines and bokeh. */
    Jungle(BrickStyle.Glossy, "EMERALD CANOPY"),

    /** Desert dunes and pyramids. */
    Dunes(BrickStyle.Sandstone, "SCORCHED DUNES"),

    /** Red mesas under a desert night sky. */
    Canyon(BrickStyle.Sandstone, "MOONLIT MESA"),

    /** Low orbit above a blue planet. */
    Orbit(BrickStyle.Neon, "LOW ORBIT"),

    /** Red nebula with asteroids. */
    Nebula(BrickStyle.Neon, "CRIMSON NEBULA"),

    /** The edge of a black hole. */
    Void(BrickStyle.Neon, "EVENT HORIZON"),
}

/** Brick kinds, stored as plain `Int`s so the grid lives in an `IntArray`. */
object BrickKind {
    const val BLUE = 0
    const val GREEN = 1
    const val ORANGE = 2
    const val PINK = 3
    const val VIOLET = 4
    const val RED = 5
    const val CYAN = 6
    const val YELLOW = 7
    const val SILVER = 8
    const val GOLD = 9
    const val STEEL = 10
    const val TNT = 11
    const val COUNT = 12

    /** Maps a level-layout character to a kind, or `-1` for an empty cell. */
    fun fromChar(c: Char): Int = when (c) {
        'b' -> BLUE
        'g' -> GREEN
        'o' -> ORANGE
        'p' -> PINK
        'v' -> VIOLET
        'r' -> RED
        'c' -> CYAN
        'y' -> YELLOW
        'S' -> SILVER
        'G' -> GOLD
        '#' -> STEEL
        'X' -> TNT
        else -> -1
    }

    /** Hits needed to destroy a brick; `-1` means indestructible. */
    fun hitPoints(kind: Int): Int = when (kind) {
        SILVER -> 2
        GOLD -> 3
        STEEL -> -1
        else -> 1
    }

    /** Base score for destroying a brick, before the combo multiplier. */
    fun points(kind: Int): Int = when (kind) {
        SILVER -> 60
        GOLD -> 100
        TNT -> 40
        else -> 20
    }
}

/** Steel bar sliding left and right under the bricks. */
class BrickMover(
    val width: Float,
    val range: Float,
    val speed: Float,
    val phase: Float,
    val gap: Float,
)

/** Level layout (13 columns) and difficulty settings. */
class BrickLevel(
    val name: String,
    val theme: BrickTheme,
    val baseSpeed: Float,
    val par: Float,
    val curseChance: Float,
    val droneEvery: Float,
    val maxDrones: Int,
    val creepSpeed: Float,
    val maxCreep: Float,
    val movers: List<BrickMover>,
    val layout: List<String>,
    val boss: BossKind? = null,
    val growFrom: Int = 0,
) {
    init {
        require(layout.size <= GRID_ROWS_MAX) { "$name has too many rows" }
        require(layout.all { it.length == GRID_COLS }) { "$name has a row that is not $GRID_COLS wide" }
        require(movers.size <= MAX_MOVERS) { "$name has too many movers" }
    }
}

private fun row(c: Char): String = c.toString().repeat(GRID_COLS)

private fun empty(n: Int): List<String> = List(n) { row('.') }

/** The ten levels, in play order. */
val BrickLevels: List<BrickLevel> = listOf(
    BrickLevel(
        name = "First Light",
        theme = BrickTheme.ForestDawn,
        baseSpeed = 760f,
        par = 100f,
        curseChance = 0.08f,
        droneEvery = 18f,
        maxDrones = 1,
        creepSpeed = 0f,
        maxCreep = 0f,
        movers = emptyList(),
        layout = listOf(
            row('b'),
            "gggSgggggSggg",
            "ooooooXoooooo",
            row('p'),
            "vvvSvvGvvSvvv",
            row('r'),
            row('c'),
            "yyyyyy.yyyyyy",
        ),
    ),
    BrickLevel(
        name = "Falling Leaves",
        theme = BrickTheme.ForestDusk,
        baseSpeed = 820f,
        par = 120f,
        curseChance = 0.14f,
        droneEvery = 13f,
        maxDrones = 2,
        creepSpeed = 0f,
        maxCreep = 0f,
        movers = listOf(BrickMover(width = 170f, range = 300f, speed = 0.9f, phase = 0f, gap = 110f)),
        layout = listOf(
            "....ooooo....",
            "..ooyyoyyoo..",
            ".oyyorroyyoo.",
            "ooyrroXorryoo",
            ".oorrSGSrroo.",
            "..ooo.#.ooo..",
            "......#......",
            ".....S#S.....",
            "..gggg#gggg..",
            "ggggXgggXgggg",
        ),
    ),
    BrickLevel(
        name = "Elder Treant",
        theme = BrickTheme.ForestNight,
        baseSpeed = 840f,
        par = 150f,
        curseChance = 0.12f,
        droneEvery = 0f,
        maxDrones = 0,
        creepSpeed = 0f,
        maxCreep = 0f,
        movers = emptyList(),
        layout = empty(8) + listOf(
            "gg.gg.G.gg.gg",
            ".S..g.X.g..S.",
            row('.'),
        ),
        boss = BossKind.Treant,
        growFrom = 8,
    ),
    BrickLevel(
        name = "Frostbite",
        theme = BrickTheme.Glacier,
        baseSpeed = 880f,
        par = 140f,
        curseChance = 0.18f,
        droneEvery = 11f,
        maxDrones = 2,
        creepSpeed = 1.2f,
        maxCreep = 90f,
        movers = listOf(BrickMover(width = 160f, range = 320f, speed = 1.0f, phase = 0f, gap = 90f)),
        layout = listOf(
            "......c......",
            "...c..S..c...",
            "....cSGSc....",
            ".c..SbbbS..c.",
            "..cSbbXbbSc..",
            "cSGbbX#XbbGSc",
            "..cSbbXbbSc..",
            ".c..SbbbS..c.",
            "....cSGSc....",
            "...c..S..c...",
            "......c......",
        ),
    ),
    BrickLevel(
        name = "Serpent Temple",
        theme = BrickTheme.Jungle,
        baseSpeed = 920f,
        par = 150f,
        curseChance = 0.22f,
        droneEvery = 9f,
        maxDrones = 3,
        creepSpeed = 0f,
        maxCreep = 0f,
        movers = listOf(
            BrickMover(width = 150f, range = 320f, speed = 1.0f, phase = 0f, gap = 90f),
            BrickMover(width = 150f, range = 320f, speed = 1.0f, phase = 3.14159f, gap = 240f),
        ),
        layout = listOf(
            "......G......",
            ".....yyy.....",
            "....SgggS....",
            "...ggXgXgg...",
            "..SbbbbbbbS..",
            ".ggg#ggg#ggg.",
            "SvvvvvGvvvvvS",
            "rrr#rrrrr#rrr",
            "ccccc...ccccc",
            ".X.........X.",
        ),
    ),
    BrickLevel(
        name = "Scarab Pharaoh",
        theme = BrickTheme.Dunes,
        baseSpeed = 900f,
        par = 170f,
        curseChance = 0.16f,
        droneEvery = 0f,
        maxDrones = 0,
        creepSpeed = 0f,
        maxCreep = 0f,
        movers = emptyList(),
        layout = empty(10) + listOf(
            "yy.yyyGyyy.yy",
            "#..S.X.X.S..#",
        ),
        boss = BossKind.Pharaoh,
        growFrom = 10,
    ),
    BrickLevel(
        name = "Totem Night",
        theme = BrickTheme.Canyon,
        baseSpeed = 960f,
        par = 160f,
        curseChance = 0.26f,
        droneEvery = 8f,
        maxDrones = 3,
        creepSpeed = 1.8f,
        maxCreep = 140f,
        movers = listOf(
            BrickMover(width = 150f, range = 320f, speed = 1.1f, phase = 0f, gap = 80f),
            BrickMover(width = 150f, range = 320f, speed = 1.1f, phase = 3.14159f, gap = 250f),
        ),
        layout = listOf(
            "r.r.r.r.r.r.r",
            row('o'),
            "oSoXoSoSoXoSo",
            row('.'),
            "..y.......y..",
            ".yGy.....yGy.",
            "..y...c...y..",
            "..y..ccc..y..",
            "..y...c...y..",
            "##.........##",
        ),
    ),
    BrickLevel(
        name = "Station Ring",
        theme = BrickTheme.Orbit,
        baseSpeed = 990f,
        par = 170f,
        curseChance = 0.28f,
        droneEvery = 7f,
        maxDrones = 3,
        creepSpeed = 2f,
        maxCreep = 120f,
        movers = listOf(
            BrickMover(width = 150f, range = 330f, speed = 1.2f, phase = 0f, gap = 80f),
            BrickMover(width = 150f, range = 330f, speed = 1.2f, phase = 3.14159f, gap = 250f),
        ),
        layout = listOf(
            "....ccccc....",
            "..cc#bbb#cc..",
            ".cbbbvvvbbbc.",
            "cbbv.....vbbc",
            "cbv..yGy..vbc",
            "#bv.yXGXy.vb#",
            "cbv..yGy..vbc",
            "cbbv.....vbbc",
            ".cbbbvvvbbbc.",
            "..cc#bbb#cc..",
            "....ccccc....",
        ),
    ),
    BrickLevel(
        name = "Dreadnought",
        theme = BrickTheme.Nebula,
        baseSpeed = 960f,
        par = 190f,
        curseChance = 0.2f,
        droneEvery = 0f,
        maxDrones = 0,
        creepSpeed = 0f,
        maxCreep = 0f,
        movers = emptyList(),
        layout = empty(7) + listOf(
            "v.v.vSXSv.v.v",
            ".p.p.p.p.p.p.",
        ),
        boss = BossKind.Dreadnought,
        growFrom = 7,
    ),
    BrickLevel(
        name = "The Void Heart",
        theme = BrickTheme.Void,
        baseSpeed = 1000f,
        par = 220f,
        curseChance = 0.22f,
        droneEvery = 0f,
        maxDrones = 0,
        creepSpeed = 0f,
        maxCreep = 0f,
        movers = emptyList(),
        layout = empty(10) + listOf(
            "v.v.v.G.v.v.v",
            row('.'),
        ),
        boss = BossKind.VoidHeart,
        growFrom = 10,
    ),
)

/** A resumable checkpoint. */
class BrickSave(
    val levelIndex: Int,
    val score: Int,
    val lives: Int,
    val bricks: IntArray?,
    val creep: Float,
)
