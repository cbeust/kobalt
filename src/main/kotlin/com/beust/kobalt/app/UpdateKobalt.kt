package com.beust.kobalt.app

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.*
import com.beust.kobalt.wrapper.Main
import java.io.File
import java.sql.Timestamp
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * Update Kobalt to the latest version.
 */
public class UpdateKobalt @Inject constructor(val github: GithubApi, val wrapperProperties: KobaltWrapperProperties) {
    fun updateKobalt() {
        val newVersion = github.latestKobaltVersion
        wrapperProperties.create(newVersion.get(), Timestamp(System.currentTimeMillis()))
        Main.main(arrayOf())
    }

    /**
     * Download from the URL found in the kobalt-wrapper.properties regardless of what the latest version is
     */
    fun downloadKobalt() {
        Main.main(arrayOf("--download", "--no-launch"))
    }

    fun checkForNewVersion(latestVersionString: String) {
        try {
            val latestVersion = Versions.toLongVersion(latestVersionString)
            val current = Versions.toLongVersion(Kobalt.version)
            val distFile = File(KFiles.joinDir(KFiles.distributionsDir, latestVersionString))
            if (latestVersion > current) {
                if (distFile.exists()) {
                    log(1, "**** Version $latestVersionString is installed")
                } else {
                    listOf("", "New Kobalt version available: $latestVersionString",
                            "To update, run ./kobaltw --update", "").forEach {
                        log(1, "**** $it")
                    }
                }
            }
            wrapperProperties.create(wrapperProperties.version, Timestamp(System.currentTimeMillis()))
        } catch(ex: TimeoutException) {
            log(2, "Didn't get the new version in time, skipping it")
        }
    }
}
