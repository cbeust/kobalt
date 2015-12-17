package com.beust.kobalt.api

/**
 * The factory function to use to instantiate all the contributors and other entities
 * found in kobalt-plugin.xml.
 */
interface IFactory {
    fun <T> instanceOf(c: Class<T>) : T
}

