package com.beust.kobalt.app.remote

import com.beust.kobalt.misc.warn
import org.slf4j.Logger
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Wakes up every `WAKE_UP_INTERVAL` and check if a certain period of time (`checkPeriod) has elapsed
 * without being rearmed. If that time has elapsed, send a QUIT command to the Kobalt server.
 */
class WatchDog(val port: Int, val checkPeriodSeconds: Long, val log: Logger) {
    private val WAKE_UP_INTERVAL: Duration = Duration.ofSeconds(60)
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/d/y HH:mm:ss")

    private var nextWakeUpMillis: Long = arm()
    private var stop: Boolean = false

    /**
     * Rearm for another `checkPeriod`.
     */
    fun rearm() {
        nextWakeUpMillis = arm()
        log.info("Watchdog rearmed for " + format(nextWakeUpMillis))
    }

    /**
     * Start the watch dog.
     */
    fun run() {
        log.info("Next wake up:" + format(nextWakeUpMillis))
        while (! stop) {
            Thread.sleep(WAKE_UP_INTERVAL.toMillis())
            val diffSeconds = (nextWakeUpMillis - System.currentTimeMillis()) / 1000
            if (diffSeconds <= 0) {
                log.info("Time to die")
                stop = true
            } else {
                log.info("Dying in $diffSeconds seconds")
            }
        }

        try {
            val connection = (URL("http://localhost:$port" + SparkServer.URL_QUIT)
                    .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
            }
            val code = connection.responseCode
            if (code == 200) {
                log.info("Successfully stopped the server")
            } else {
                warn("Couldn't stop the server, response: " + code)
            }
        } catch(ex: Exception) {
            warn("Couldn't stop the server: " + ex.message, ex)
        }
    }

    private fun arm() = System.currentTimeMillis() + (checkPeriodSeconds * 1000)

    private fun toLocalDate(millis: Long) = LocalDateTime.ofEpochSecond(millis / 1000, 0, OffsetDateTime.now().offset)

    private fun format(millis: Long) = FORMAT.format(toLocalDate(millis))
}