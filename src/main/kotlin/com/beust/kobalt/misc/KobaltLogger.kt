package com.beust.kobalt.misc

import com.beust.kobalt.api.Kobalt
import java.text.SimpleDateFormat
import java.util.*

internal interface KobaltLogger {
    companion object {
        public var LOG_LEVEL : Int = 1

        val logger : Logger get() =
            if (Kobalt.context != null) {
                Logger(Kobalt.context!!.args.dev)
            } else {
                Logger(false)
            }

        fun log(level: Int, s: String) {
            if (level <= LOG_LEVEL) {
                logger.log("Logger", s)
            }
        }

        fun warn(s: String, e: Throwable? = null) {
            logger.warn("Logger", s, e)
        }

        fun debug(s: String) {
            logger.debug(s)
        }
    }

    final fun log(level: Int = 1, message: String) {
        if (level <= LOG_LEVEL) {
            logger.log("Logger", message)
        }
    }

    final fun debug(message: String) {
        logger.debug(message)
    }

    final fun error(message: String, e: Throwable? = null) {
        logger.error("Logger", "***** $message", e)
    }

    final fun warn(message: String, e: Throwable? = null) {
        logger.warn("Logger", message, e)
    }
}

fun Any.log(level: Int, text: String) {
    if (level <= KobaltLogger.LOG_LEVEL) {
        KobaltLogger.logger.log(javaClass.simpleName, text)
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

class Logger(val dev: Boolean) {
    val FORMAT = SimpleDateFormat("HH:mm:ss.SSS")

    private fun getPattern(type: String, tag: String, message: String) =
        if (dev) {
            val ts = FORMAT.format(Date())
            "$type/$ts [" + Thread.currentThread().name + "] $tag - $message"
        } else {
            message
        }

    final fun debug(tag: String, message: String) =
        println(getPattern("D", tag, message))

    final fun error(tag: String, message: String, e: Throwable? = null) =
        println(getPattern("***** E", tag, message) + " Exception: " + e?.getMessage())

    final fun warn(tag: String, message: String, e: Throwable? = null) =
        println(getPattern("W", tag, message))

    final fun log(tag: String, message: String) =
        println(getPattern("L", tag, message))
}
