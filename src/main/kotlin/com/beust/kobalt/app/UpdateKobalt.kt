package com.beust.kobalt.app

import com.beust.kobalt.misc.GithubApi
import com.beust.kobalt.misc.KobaltWrapperProperties
import com.beust.kobalt.wrapper.Main
import javax.inject.Inject

/**
 * Update Kobalt to the latest version.
 */
public class UpdateKobalt @Inject constructor(val github: GithubApi, val wrapperProperties: KobaltWrapperProperties) {
    fun updateKobalt() {
        val newVersion = github.latestKobaltVersion
        wrapperProperties.create(newVersion.get())
        Main.main(arrayOf())
    }

    /**
     * Download from the URL found in the kobalt-wrapper.properties regardless of what the latest version is
     */
    fun downloadKobalt() {
        Main.main(arrayOf("--download", "--no-launch"))
    }
}
