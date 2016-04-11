package com.beust.kobalt.misc

import com.google.common.base.CharMatcher

class Strings {
    companion object {
        fun pluralize(n:Int, string: String, plural: String = string + "s") = if (n != 1) plural else string
        fun pluralizeAll(n:Int, string: String, plural: String = string + "s") = "$n " + pluralize(n, string, plural)
    }

}

/**
 * @Return the number of times the given character occurs in the string
 */
infix fun String.countChar(c: Char) : Int {
    return CharMatcher.`is`(c).countIn(this)
}
