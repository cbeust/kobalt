package com.beust.kobalt.misc

import com.google.common.base.CharMatcher

public class Strings {
    companion object {
        fun <T> join(separator: String, strings: List<T>): String {
            var result = StringBuffer()
            var i = 0
            strings.forEach {
                if (i++ > 0) {
                    result.append(separator)
                }
                result.append(it)
            }
            return result.toString()
        }

        fun isEmpty(s: String?): Boolean {
            return s == null || s.isEmpty()
        }
    }

}

/**
 * @Return the number of times the given character occurs in the string
 */
public infix fun String.countChar(c: Char): Int {
    return CharMatcher.`is`(c).countIn(this)
}
