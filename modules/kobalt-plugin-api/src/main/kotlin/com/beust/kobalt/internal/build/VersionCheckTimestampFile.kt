package com.beust.kobalt.internal.build

import com.beust.kobalt.misc.KFiles
import java.io.File
import java.time.Instant

class VersionCheckTimestampFile {
    companion object {
        private val KOBALT_VERSION_CHECK_TIMESTAMP_FILE = "versionCheckTimestamp.txt"
        private val CHECK_TIMESTAMP_FILE = File(KFiles.KOBALT_DOT_DIR, KOBALT_VERSION_CHECK_TIMESTAMP_FILE)

        fun updateTimestamp(timestamp: Instant) = KFiles.saveFile(CHECK_TIMESTAMP_FILE, timestamp.toString())

        val timestamp : Instant
            get() = if (CHECK_TIMESTAMP_FILE.exists()) {
                Instant.parse(CHECK_TIMESTAMP_FILE.readText())
            } else {
                updateTimestamp(Instant.MIN)
                Instant.MIN
            }
    }
}