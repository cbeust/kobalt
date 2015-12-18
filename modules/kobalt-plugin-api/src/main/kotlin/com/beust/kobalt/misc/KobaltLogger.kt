package com.beust.kobalt.misc

import com.beust.kobalt.AsciiArt
import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Kobalt
import java.text.SimpleDateFormat
import java.util.*

fun Any.log(level: Int, text: String, newLine : Boolean = true) {
    if (level <= KobaltLogger.LOG_LEVEL) {
        KobaltLogger.logger.log(javaClass.simpleName, text, newLine)
    }
}

fun Any.logWrap(level: Int, text1: String, text2: String, function: () -> Unit) {
    if (level <= KobaltLogger.LOG_LEVEL) {
        KobaltLogger.logger.log(javaClass.simpleName, text1, newLine = false)
    }
    function()
    if (level <= KobaltLogger.LOG_LEVEL) {
        KobaltLogger.logger.log(javaClass.simpleName, text2, newLine = true)
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

    private fun getPattern(shortTag: String, shortMessage: String, longMessage: String, tag: String) =
        if (dev) {
            val ts = FORMAT.format(Date())
            "$shortTag/$ts [" + Thread.currentThread().name + "] $tag - $shortMessage"
        } else {
            longMessage
        }

    final fun debug(tag: String, message: String) =
        println(getPattern("D", message, message, tag))

    final fun error(tag: String, message: String, e: Throwable? = null) {
        val docUrl = if (e is KobaltException && e.docUrl != null) e.docUrl else null
        val text = if (! message.isBlank()) message
            else if (e != null && (! e.message.isNullOrBlank())) e.message
            else { "<unknown error>" }
        val shortMessage = "***** E $text " + if (docUrl != null) " Documentation: $docUrl" else ""
        val longMessage = "*****\n***** ERROR $text\n*****"

        println(AsciiArt.errorColor(getPattern("E", shortMessage, longMessage, tag)))
        if (KobaltLogger.LOG_LEVEL > 1) {
            e?.printStackTrace()
        }
    }

    final fun warn(tag: String, message: String, e: Throwable? = null) {
        val fullMessage = "***** WARNING " + (e?.message ?: message)
        println(AsciiArt.Companion.warnColor(getPattern("W", fullMessage, fullMessage, tag)))
    }

    final fun log(tag: String, message: String, newLine: Boolean) =
        with(getPattern("L", message, message, tag)) {
            if (newLine) println(this)
            else print("\r" + this)
        }
}
