package com.beust.kobalt.misc

import java.io.File
import javax.inject.Inject

/**
 * Update Kobalt to the latest version.
 */
public class UpdateKobalt @Inject constructor(val github: GithubApi) {
    fun updateKobalt() {
        val newVersion = github.latestKobaltVersion
        KFiles.saveFile(File("kobalt/wrapper/kobalt-wrapper.properties"), "kobalt.version=${newVersion.get()}")
        com.beust.kobalt.wrapper.main(arrayOf())
    }
}
