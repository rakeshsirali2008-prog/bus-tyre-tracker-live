package com.bustyre.tracker.data

import java.math.BigInteger

/**
 * Orders vehicle numbers by letters first, then by the number as a whole value.
 * F9 comes before F10, and F3333, F3334, F3335 come in that order.
 */
object VehicleSort : Comparator<String> {

    private val pattern = Regex("^([^0-9]*)([0-9]*)(.*)$")

    private fun parts(s: String): Triple<String, String, String> {
        val m = pattern.matchEntire(s) ?: return Triple(s, "", "")
        return Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    override fun compare(a: String, b: String): Int {
        val (prefixA, digitsA, restA) = parts(a)
        val (prefixB, digitsB, restB) = parts(b)

        var c = prefixA.compareTo(prefixB)
        if (c != 0) return c

        c = when {
            digitsA.isEmpty() && digitsB.isEmpty() -> 0
            digitsA.isEmpty() -> -1
            digitsB.isEmpty() -> 1
            else -> BigInteger(digitsA).compareTo(BigInteger(digitsB))
        }
        if (c != 0) return c

        c = restA.compareTo(restB)
        if (c != 0) return c

        return a.compareTo(b)
    }
}
