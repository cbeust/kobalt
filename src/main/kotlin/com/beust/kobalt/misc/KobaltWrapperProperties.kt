package com.beust.kobalt.misc

import com.google.inject.Inject
import java.io.File

class KobaltWrapperProperties @Inject constructor() {
    private val WRAPPER_DIR = KFiles.KOBALT_DIR + "/wrapper"
    private val KOBALT_WRAPPER_PROPERTIES = "kobalt-wrapper.properties"
    private val PROPERTY_VERSION = "kobalt.version"
    private val PROPERTY_DOWNLOAD_URL = "kobalt.downloadUrl"

    val file: File
            get() = File("$WRAPPER_DIR/$KOBALT_WRAPPER_PROPERTIES")

    fun create(version: String) {
        KFiles.saveFile(file, listOf(
                "$PROPERTY_VERSION=$version",
                "$PROPERTY_DOWNLOAD_URL"
        ).joinToString("\n"))
    }
}
