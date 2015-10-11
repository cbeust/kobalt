package com.beust.kobalt.misc

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public interface KobaltLogger {
    val logger : Logger
        get() = LoggerFactory.getLogger(javaClass.simpleName)

    companion object {

        public var LOG_LEVEL : Int = 1

        fun log(level: Int, s: String) {
            if (level <= LOG_LEVEL) {
                LoggerFactory.getLogger(KobaltLogger::class.java.simpleName).info(s)
            }
        }

        fun warn(s: String, e: Throwable? = null) {
            LoggerFactory.getLogger(KobaltLogger::class.java.simpleName).warn(s, e)
        }

        fun debug(s: String) {
            LoggerFactory.getLogger(KobaltLogger::class.java.simpleName).debug(s)
        }
    }

    final fun log(level: Int = 1, message: String) {
        if (level <= LOG_LEVEL) {
            logger.info(message)
        }
    }

    final fun debug(message: String) {
        logger.debug(message)
    }

    final fun error(message: String, e: Throwable? = null) {
        logger.error("***** $message", e)
    }

    final fun warn(message: String, e: Throwable? = null) {
        logger.warn(message, e)
    }
}
