package com.beust.kobalt.misc

import com.beust.kobalt.Args
import com.beust.kobalt.AsciiArt
import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.aether.Exceptions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Any.log(level: Int, text: CharSequence, newLine : Boolean = true) {
    if (level <= KobaltLogger.LOG_LEVEL) {
        KobaltLogger.logger.log(javaClass.simpleName, text, newLine)
    }
}

fun Any.kobaltLog(level: Int, text: CharSequence, newLine : Boolean = true) = log(level, text, newLine)
fun Any.kobaltWarn(text: CharSequence) = warn(text)
fun Any.kobaltError(text: CharSequence) = error(text)
fun Any.kobaltLog(tag: String, text: CharSequence, newLine : Boolean = true) {
    if (Kobalt.INJECTOR.getInstance(Args::class.java).logTags.split(',').contains(tag)) {
        log(1, text, newLine)
    }
}

fun Any.logWrap(level: Int, text1: CharSequence, text2: CharSequence, function: () -> Unit) {
    if (level <= KobaltLogger.LOG_LEVEL) {
        KobaltLogger.logger.log(javaClass.simpleName, text1, newLine = false)
    }
    function()
    if (level <= KobaltLogger.LOG_LEVEL) {
        KobaltLogger.logger.log(javaClass.simpleName, text2, newLine = true)
    }
}

fun Any.debug(text: CharSequence) {
    KobaltLogger.logger.debug(javaClass.simpleName, text)
}

fun Any.warn(text: CharSequence, exception: Exception? = null) {
    KobaltLogger.logger.warn(javaClass.simpleName, text, exception)
}

fun Any.kobaltError(text: CharSequence, e: Throwable? = null) = error(text, e)

fun Any.error(text: CharSequence, e: Throwable? = null) {
    KobaltLogger.logger.error(javaClass.simpleName, text, e)
}

object KobaltLogger {
    var LOG_LEVEL: Int = 1

    val logger: Logger get() =
    if (Kobalt.context != null) {
        Logger(Kobalt.context!!.args.dev)
    } else {
        Logger(false)
    }
}

class Logger(val dev: Boolean) {
    val FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private fun getPattern(shortTag: String, shortMessage: CharSequence, longMessage: CharSequence, tag: String) =
        if (dev) {
            val ts = LocalDateTime.now().format(FORMAT)
            "$shortTag/$ts [" + Thread.currentThread().name + "] $tag - $shortMessage"
        } else {
            longMessage
        }

    final fun debug(tag: String, message: CharSequence) =
        println(getPattern("D", message, message, tag))

    final fun error(tag: String, message: CharSequence, e: Throwable? = null) {
        val docUrl = if (e is KobaltException && e.docUrl != null) e.docUrl else null
        val text = if (! message.isBlank()) message
            else if (e != null && (! e.message.isNullOrBlank())) e.message
            else { "<unknown error>" }
        val shortMessage = "***** E $text " + if (docUrl != null) " Documentation: $docUrl" else ""
        val longMessage = "*****\n***** ERROR $text\n*****"

        println(AsciiArt.errorColor(getPattern("E", shortMessage, longMessage, tag)))
        if (KobaltLogger.LOG_LEVEL > 1) {
            val t = e as Throwable
            Exceptions.printStackTrace(t)
//            e?.printStackTrace()
        }
    }

    final fun warn(tag: String, message: CharSequence, e: Throwable? = null) {
        val fullMessage = "***** WARNING " + (e?.message ?: message)
        println(AsciiArt.Companion.warnColor(getPattern("W", fullMessage, fullMessage, tag)))
        if (KobaltLogger.LOG_LEVEL > 1) {
            Exceptions.printStackTrace(e!!)
        }
    }

    final fun log(tag: String, message: CharSequence, newLine: Boolean) =
        with(getPattern("L", message, message, tag)) {
            if (newLine) println(this)
            else print("\r" + this)
        }
}
