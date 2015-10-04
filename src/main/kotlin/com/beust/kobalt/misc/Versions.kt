package com.beust.kobalt.misc

import com.google.common.base.CharMatcher

public class Versions {
    companion object {
        /**
         * Turn "6.9.4" into 600090004
         */
        public fun toLongVersion(version: String) : Long {
            val count = version.countChar('.')
            val normalizedVersion = if (count == 2) version else if (count == 1) version + ".0"
            else version + ".0.0"

            fun parseLong(s: String, radix: Int) : Long {
                try {
                    return java.lang.Long.parseLong(s, radix)
                } catch(ex: NumberFormatException) {
                    KobaltLogger.warn("Couldn't parse version \"${version}\"")
                    return 0L
                }
            }

            return normalizedVersion
                    .split(".")
                    .take(3)
                    .map {
                        val s = CharMatcher.inRange('0', '9').or(CharMatcher.`is`('.')).retainFrom(it)
                        parseLong(s, 10)
                    }
                    .fold(0L, { n, s -> s + n * 10000 })
        }
    }
}
