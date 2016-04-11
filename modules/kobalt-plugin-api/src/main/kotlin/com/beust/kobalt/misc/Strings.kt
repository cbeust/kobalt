package com.beust.kobalt.misc

import com.google.common.base.CharMatcher

class Strings {
    companion object {
        fun pluralize(n:Int, s: String, plural: String = s) = plural + (if (n != 1) "s" else "")
        fun pluralizeAll(n:Int, s: String, plural: String = s) = "$n " + pluralize(n, s, plural)
    }

}

/**
 * @Return the number of times the given character occurs in the string
 */
infix fun String.countChar(c: Char) : Int {
    return CharMatcher.`is`(c).countIn(this)
}
