package com.beust.kobalt.misc

import com.google.common.base.CharMatcher

public class Strings {
    companion object {
        fun pluralize(s: String, n: Int) = s + (if (n != 1) "s" else "")
    }

}

/**
 * @Return the number of times the given character occurs in the string
 */
public infix fun String.countChar(c: Char) : Int {
    return CharMatcher.`is`(c).countIn(this)
}
