package com.beust.kobalt.misc

import javax.inject.Inject

/**
 * Update Kobalt to the latest version.
 */
public class UpdateKobalt @Inject constructor(val github: GithubApi, val wrapperProperties: KobaltWrapperProperties) {
    fun updateKobalt() {
        val newVersion = github.latestKobaltVersion
        wrapperProperties.create(newVersion.get())
        com.beust.kobalt.wrapper.Main.main(arrayOf())
    }

    /**
     * Download from the URL found in the kobalt-wrapper.properties regardless of what the latest version is
     */
    fun downloadKobalt() {
        com.beust.kobalt.wrapper.Main.main(arrayOf("--download"))
    }
}
