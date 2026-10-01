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

package com.zainkhalid.brickbreaker.data

import android.content.Context
import androidx.core.content.edit
import com.zainkhalid.brickbreaker.engine.BrickLevels
import com.zainkhalid.brickbreaker.engine.BrickSave

private const val PREFS = "brick_breaker"
private const val KEY_LEVEL = "save_level"
private const val KEY_SCORE = "save_score"
private const val KEY_LIVES = "save_lives"
private const val KEY_BRICKS = "save_bricks"
private const val KEY_CREEP = "save_creep"
private const val KEY_BEST = "best_score"
private const val KEY_STARS = "stars_"
private const val KEY_UNLOCKED = "unlocked_level"

/** Save point, best score and stars per level. */
class BrickSaveStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The current checkpoint, or `null` if there is nothing to continue. */
    fun load(): BrickSave? {
        if (!prefs.contains(KEY_LEVEL)) return null
        val level = prefs.getInt(KEY_LEVEL, -1)
        if (level !in BrickLevels.indices) return null
        val bricks = prefs.getString(KEY_BRICKS, null)
            ?.takeIf { it.isNotEmpty() }
            ?.split(',')
            ?.map { it.toIntOrNull() ?: return null }
            ?.toIntArray()
        return BrickSave(
            levelIndex = level,
            score = prefs.getInt(KEY_SCORE, 0),
            lives = prefs.getInt(KEY_LIVES, 3),
            bricks = bricks,
            creep = prefs.getFloat(KEY_CREEP, 0f),
        )
    }

    /** Writes a checkpoint, or clears it when [save] is `null` (the run ended). */
    fun save(save: BrickSave?) {
        if (save == null) {
            clear()
            return
        }
        prefs.edit {
            putInt(KEY_LEVEL, save.levelIndex)
            putInt(KEY_SCORE, save.score)
            putInt(KEY_LIVES, save.lives)
            putString(KEY_BRICKS, save.bricks?.joinToString(",") ?: "")
            putFloat(KEY_CREEP, save.creep)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_LEVEL)
            remove(KEY_SCORE)
            remove(KEY_LIVES)
            remove(KEY_BRICKS)
            remove(KEY_CREEP)
        }
    }

    var best: Int
        get() = prefs.getInt(KEY_BEST, 0)
        set(value) {
            if (value > best) prefs.edit { putInt(KEY_BEST, value) }
        }

    /** Best stars (0 to 3) per level. */
    fun stars(): List<Int> = BrickLevels.indices.map { prefs.getInt(KEY_STARS + it, 0) }

    /** Highest level index the player can pick from the menu. */
    val unlocked: Int
        get() {
            var top = prefs.getInt(KEY_UNLOCKED, 0)
            // Older installs only have stars and the save point to go on.
            for (i in BrickLevels.indices) if (prefs.getInt(KEY_STARS + i, 0) > 0) top = maxOf(top, i + 1)
            load()?.let { top = maxOf(top, it.levelIndex) }
            return top.coerceIn(0, BrickLevels.lastIndex)
        }

    fun unlock(level: Int) {
        if (level > prefs.getInt(KEY_UNLOCKED, 0)) prefs.edit { putInt(KEY_UNLOCKED, level) }
    }

    fun recordStars(level: Int, stars: Int) {
        if (stars > prefs.getInt(KEY_STARS + level, 0)) prefs.edit { putInt(KEY_STARS + level, stars) }
    }
}
