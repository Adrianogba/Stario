/*
 * Copyright (C) 2025 Răzvan Albu
 * Copyright (C) 2026 Adriano Pontes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package adrianogba.stario.launcher.sheet.drawer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expected values come from the table in JaroWinklerDistance's own
 * documentation, which predates the Kotlin rewrite. They exist to catch the
 * conversion having quietly changed the arithmetic, which would show up as
 * app search stopping matching rather than as anything failing to build.
 */
class JaroWinklerDistanceTest {

    private fun score(left: String, right: String) =
        JaroWinklerDistance.getScore(left, right)

    @Test
    fun `documented scores are unchanged`() {
        assertEquals(0.0, score("", ""), 0.0)
        assertEquals(0.0, score("", "a"), 0.0)
        assertEquals(0.0, score("aaapppp", ""), 0.0)
        assertEquals(0.93, score("frog", "fog"), 0.0)
        assertEquals(0.0, score("fly", "ant"), 0.0)
        assertEquals(0.44, score("elephant", "hippo"), 0.0)
        assertEquals(0.44, score("hippo", "elephant"), 0.0)
        assertEquals(0.0, score("hippo", "zzzzzzzz"), 0.0)
        assertEquals(0.88, score("hello", "hallo"), 0.0)
        assertEquals(0.93, score("ABC Corporation", "ABC Corp"), 0.0)
        assertEquals(0.95, score("D N H Enterprises Inc", "D & H Enterprises, Inc."), 0.0)
        assertEquals(
            0.92,
            score("My Gym Children's Fitness Center", "My Gym. Childrens Fitness"),
            0.0
        )
        assertEquals(0.88, score("PENNSYLVANIA", "PENNCISYLVNIA"), 0.0)
    }

    @Test
    fun `identical strings score one`() {
        assertEquals(1.0, score("settings", "settings"), 0.0)
    }

    @Test
    fun `scoring is symmetric`() {
        assertEquals(score("elephant", "hippo"), score("hippo", "elephant"), 0.0)
        assertEquals(score("frog", "fog"), score("fog", "frog"), 0.0)
    }

    @Test
    fun `scores stay within zero and one`() {
        val words = listOf("settings", "settigs", "maps", "", "a", "zzzzzzzzzz", "Play Store")

        for (left in words) {
            for (right in words) {
                val value = score(left, right)

                assertTrue("$left vs $right produced $value", value in 0.0..1.0)
            }
        }
    }

    /**
     * AppAdapter treats anything above 0.87 as a match. This is the typo case
     * that has to keep working: without it, searching a misspelled app name
     * silently stops finding the app.
     */
    @Test
    fun `common typo still clears the AppAdapter threshold`() {
        assertTrue(score("settings", "settigs") > 0.87)
        assertTrue(score("calculator", "calcualtor") > 0.87)
    }

    @Test
    fun `unrelated words stay below the AppAdapter threshold`() {
        assertTrue(score("settings", "maps") < 0.87)
        assertTrue(score("camera", "zzzzzz") < 0.87)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `null left argument is rejected`() {
        JaroWinklerDistance.getScore(null, "a")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `null right argument is rejected`() {
        JaroWinklerDistance.getScore("a", null)
    }
}
