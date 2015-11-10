package com.beust.kobalt.misc

import javax.inject.Inject

/**
 * Update Kobalt to the latest version.
 */
public class UpdateKobalt @Inject constructor(val github: GithubApi, val kobaltWrapperProperties: KobaltWrapperProperties) {
    fun updateKobalt() {
        val newVersion = github.latestKobaltVersion
        kobaltWrapperProperties.maybeCreate(newVersion.get())
        com.beust.kobalt.wrapper.main(arrayOf())
    }
}
