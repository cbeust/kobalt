package com.beust.kobalt.internal.build

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.KFiles
import java.io.File

class VersionFile {
    companion object {
        private val VERSION_FILE = "version.txt"

        fun generateVersionFile(directory: File) {
            KFiles.saveFile(File(directory, VERSION_FILE), Kobalt.version)
        }

        fun isSameVersionFile(directory: File) =
                with(File(directory, VERSION_FILE)) {
                    !exists() || (exists() && readText() == Kobalt.version)
                }
    }
}