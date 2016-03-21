package com.beust.kobalt.internal

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.Io
import com.beust.kobalt.misc.KFiles
import java.io.File

/**
 * Clean up old Kobalt files.
 */
class Gc {
    fun run() {
        val io = Io(dryRun = false)

        // Delete all the files that don't have kobalt-$version in them
        val version = Kobalt.version
        val name = "kobalt-$version"
        val dist = File(KFiles.distributionsDir)
        io.rmDir(dist) { f: File -> f.absolutePath.contains(name) }
    }
}
