package com.beust.kobalt.misc

import java.lang.Long
import java.lang.NumberFormatException
import java.util.*

/**
 * Compare string versions, e.g. "1.2.0", "0.9", etc...
 */
class StringVersion(val version: String) : Comparable<StringVersion> {
    override fun compareTo(other: StringVersion): Int {
        val s1 = arrayListOf<String>().apply { addAll(version.split('.')) }
        val s2 = arrayListOf<String>().apply { addAll(other.version.split('.')) }

        // Normalize both strings, so they have the same length, e.g. 1 -> 1.0.0
        val max = Math.max(s1.size, s2.size)
        val shorterList : ArrayList<String> = if (s1.size == max) s2 else s1
        repeat(max - shorterList.size) {
            shorterList.add("0")
        }

        // Compare each section
        repeat(max) { index ->
            try {
                fun parse(s: String) = Long.parseLong(s.filter(Char::isDigit))

                val v1 = parse(s1[index])
                val v2 = parse(s2[index])
                if (v1 < v2) return -1
                else if (v1 > v2) return 1
            } catch(ex: NumberFormatException) {
                warn("Couldn't parse version $version or $other")
                return -1
            }
        }
        return 0
    }

    override fun equals(other: Any?) =
        if (other is StringVersion) this.compareTo(other) == 0
        else false

    override fun hashCode() = version.hashCode()

    override fun toString() = version
}
