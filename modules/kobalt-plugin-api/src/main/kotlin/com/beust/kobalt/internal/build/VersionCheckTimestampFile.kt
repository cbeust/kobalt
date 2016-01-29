package com.beust.kobalt.internal.build

import com.beust.kobalt.misc.KFiles
import java.io.File
import java.time.Instant

class VersionCheckTimestampFile {
    companion object {
        private val KOBALT_VERSION_CHECK_TIMESTAMP_FILE = "versionCheckTimestamp.txt"
        private val checkTimestampFile = File(KFiles.KOBALT_DOT_DIR, KOBALT_VERSION_CHECK_TIMESTAMP_FILE)

        fun updateTimestamp(timestamp: Instant) = KFiles.saveFile(checkTimestampFile, timestamp.toString())

        fun getTimestamp(): Instant {
            return if(checkTimestampFile.exists())
                Instant.parse(checkTimestampFile.readText())
            else {
                updateTimestamp(Instant.MIN)
                Instant.MIN
            }
        }
    }
}