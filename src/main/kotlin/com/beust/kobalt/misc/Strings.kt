package com.beust.kobalt.misc

/**
 * @Return the number of times the given character occurs in the string
 */
public infix fun String.countChar(c: Char) : Int = count { it == c }