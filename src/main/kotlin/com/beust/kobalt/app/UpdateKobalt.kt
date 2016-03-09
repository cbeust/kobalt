package com.beust.kobalt.app

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.internal.build.VersionCheckTimestampFile
import com.beust.kobalt.misc.*
import com.beust.kobalt.wrapper.Main
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * Update Kobalt to the latest version.
 */
class UpdateKobalt @Inject constructor(val github: GithubApi, val wrapperProperties: KobaltWrapperProperties) {
    fun updateKobalt() {
        val newVersion = github.latestKobaltVersion
        wrapperProperties.create(newVersion.get())
        VersionCheckTimestampFile.updateTimestamp(Instant.now())
        Main.main(arrayOf())
    }

    /**
     * Download from the URL found in the kobalt-wrapper.properties regardless of what the latest version is
     */
    fun downloadKobalt() {
        Main.main(arrayOf("--download", "--no-launch"))
    }

    /**
     * Accepts Future<String> as `latestVersionFuture` to allow getting `latestVersion` in the background.
     */
    fun checkForNewVersion(latestVersionFuture: Future<String>) {
        if (Kobalt.versionCheckTimeout > Duration.between(VersionCheckTimestampFile.timestamp, Instant.now())) {
            return  // waits `Kobalt.versionCheckTimeout` before the next check
        }

        try {
            val latestVersionString = latestVersionFuture.get()
            val latestVersion = Versions.toLongVersion(latestVersionString)
            val current = Versions.toLongVersion(Kobalt.version)
            val distFile = File(KFiles.distributionsDir)
            if (latestVersion > current) {
                if (distFile.exists()) {
                    log(1, "**** Version $latestVersionString is installed, you can switch to it with " +
                            "./kobaltw --update")
                } else {
                    listOf("", "New Kobalt version available: $latestVersionString",
                            "To update, run ./kobaltw --update", "").forEach {
                        log(1, "**** $it")
                    }
                }
            }
            VersionCheckTimestampFile.updateTimestamp(Instant.now())
        } catch(ex: TimeoutException) {
            log(2, "Didn't get the new version in time, skipping it")
        }
    }
}
