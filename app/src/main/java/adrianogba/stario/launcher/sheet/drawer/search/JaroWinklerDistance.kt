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

import java.util.Arrays
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object JaroWinklerDistance {
    /**
     * Find the Jaro Winkler Distance which indicates the similarity score
     * between two CharSequences.
     *
     * ```
     * distance.apply(null, null)          = IllegalArgumentException
     * distance.apply("","")               = 0.0
     * distance.apply("","a")              = 0.0
     * distance.apply("aaapppp", "")       = 0.0
     * distance.apply("frog", "fog")       = 0.93
     * distance.apply("fly", "ant")        = 0.0
     * distance.apply("elephant", "hippo") = 0.44
     * distance.apply("hippo", "elephant") = 0.44
     * distance.apply("hippo", "zzzzzzzz") = 0.0
     * distance.apply("hello", "hallo")    = 0.88
     * distance.apply("ABC Corporation", "ABC Corp") = 0.93
     * distance.apply("D N H Enterprises Inc", "D & H Enterprises, Inc.") = 0.95
     * distance.apply("My Gym Children's Fitness Center", "My Gym. Childrens Fitness") = 0.92
     * distance.apply("PENNSYLVANIA", "PENNCISYLVNIA")    = 0.88
     * ```
     *
     * @param left  the first String, must not be null
     * @param right the second String, must not be null
     * @return result distance
     * @throws IllegalArgumentException if either String input `null`
     */
    @JvmStatic
    fun getScore(left: CharSequence?, right: CharSequence?): Double {
        val defaultScalingFactor = 0.1
        val percentageRoundValue = 100.0

        require(left != null && right != null) { "Strings must not be null" }

        val mtp = matches(left, right)
        val m = mtp[0].toDouble()

        if (m == 0.0) {
            return 0.0
        }

        val j = (m / left.length + m / right.length + (m - mtp[1]) / m) / 3
        val jw = if (j < 0.7) {
            j
        } else {
            j + min(defaultScalingFactor, 1.0 / mtp[3]) * mtp[2] * (1.0 - j)
        }

        return (jw * percentageRoundValue).roundToLong() / percentageRoundValue
    }

    /**
     * This method returns the Jaro-Winkler string matches, transpositions, prefix, max array.
     *
     * @param first  the first string to be matched
     * @param second the second string to be machted
     * @return mtp array containing: matches, transpositions, prefix, and max length
     */
    @JvmStatic
    fun matches(first: CharSequence, second: CharSequence): IntArray {
        val longer: CharSequence
        val shorter: CharSequence

        if (first.length > second.length) {
            longer = first
            shorter = second
        } else {
            longer = second
            shorter = first
        }

        val range = max(longer.length / 2 - 1, 0)
        val matchIndexes = IntArray(shorter.length)
        Arrays.fill(matchIndexes, -1)
        val matchFlags = BooleanArray(longer.length)
        var matches = 0

        for (mi in 0 until shorter.length) {
            val c1 = shorter[mi]

            for (xi in max(mi - range, 0) until min(mi + range + 1, longer.length)) {
                if (!matchFlags[xi] && c1 == longer[xi]) {
                    matchIndexes[mi] = xi
                    matchFlags[xi] = true
                    matches++

                    break
                }
            }
        }

        val ms1 = CharArray(matches)
        val ms2 = CharArray(matches)

        var si = 0
        for (i in 0 until shorter.length) {
            if (matchIndexes[i] != -1) {
                ms1[si] = shorter[i]
                si++
            }
        }

        si = 0
        for (i in 0 until longer.length) {
            if (matchFlags[i]) {
                ms2[si] = longer[i]
                si++
            }
        }

        var transpositions = 0
        for (mi in ms1.indices) {
            if (ms1[mi] != ms2[mi]) {
                transpositions++
            }
        }

        var prefix = 0
        for (mi in 0 until shorter.length) {
            if (first[mi] == second[mi]) {
                prefix++
            } else {
                break
            }
        }

        return intArrayOf(matches, transpositions / 2, prefix, longer.length)
    }
}
