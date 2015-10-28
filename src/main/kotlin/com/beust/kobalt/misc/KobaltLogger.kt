package com.beust.kobalt.misc

import com.beust.kobalt.api.Kobalt
import java.text.SimpleDateFormat
import java.util.*

fun Any.log(level: Int, text: String, newLine : Boolean = true) {
    if (level <= KobaltLogger.LOG_LEVEL) {
        KobaltLogger.logger.log(javaClass.simpleName, text, newLine)
    }
}

fun Any.debug(text: String) {
    KobaltLogger.logger.debug(javaClass.simpleName, text)
}

fun Any.warn(text: String) {
    KobaltLogger.logger.warn(javaClass.simpleName, text)
}

fun Any.error(text: String, e: Throwable? = null) {
    KobaltLogger.logger.error(javaClass.simpleName, text, e)
}

object KobaltLogger {
    public var LOG_LEVEL: Int = 1

    val logger: Logger get() =
    if (Kobalt.context != null) {
        Logger(Kobalt.context!!.args.dev)
    } else {
        Logger(false)
    }
}

class Logger(val dev: Boolean) {
    val FORMAT = SimpleDateFormat("HH:mm:ss.SSS")

    private fun getPattern(type: String, devType: String, tag: String, message: String) =
        if (dev) {
            val ts = FORMAT.format(Date())
            "$type/$ts [" + Thread.currentThread().name + "] $tag - $message"
        } else {
            devType + message
        }

    final fun debug(tag: String, message: String) =
        println(getPattern("D", "Debug ", tag, message))

    final fun error(tag: String, message: String, e: Throwable? = null) =
        println(getPattern("***** E", "***** ERROR ", tag, message) +
                if (e != null) " Exception: " + e.message else "")

    final fun warn(tag: String, message: String, e: Throwable? = null) =
        println(getPattern("W", "***** WARNING ", tag, message))

    final fun log(tag: String, message: String, newLine: Boolean) =
        with(getPattern("L", "", tag, message)) {
            if (newLine) println(this)
            else print("\r" + this)
        }
}
