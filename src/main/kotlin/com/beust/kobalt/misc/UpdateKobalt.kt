package com.beust.kobalt.misc

import com.beust.kobalt.maven.Http
import com.google.inject.Inject
import java.io.File

/**
 * Update Kobalt to the latest version.
 */
public class UpdateKobalt @Inject constructor(val http: Http, val github: GithubApi) {
    fun updateKobalt() {
        val newVersion = github.latestKobaltVersion
        KFiles.saveFile(File("kobalt/wrapper/kobalt-wrapper.properties"), "kobalt.version=${newVersion.get()}")
        com.beust.kobalt.wrapper.main(arrayOf())
    }
}
