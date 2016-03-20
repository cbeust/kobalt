package com.beust.kobalt.internal

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.Io
import com.beust.kobalt.misc.KFiles
import java.io.File
import java.nio.file.Files

/**
 * Clean up old Kobalt files.
 */
class Gc {
    fun run() {
        // Delete all the distributions except the current one
        val dist = KFiles.distributionsDir
        val version = Kobalt.version
        val tmpDir = Files.createTempDirectory("kobalt")

        // Zipfile
        val io = Io(dryRun = true)
        val zipFileName = "kobalt-$version.zip"
        io.moveFile(File(KFiles.joinDir(dist, zipFileName)), tmpDir.toFile())

        // Distribution directory
        val zipDirectoryName = "kobalt-$version"
        val zipDirectory = File(KFiles.joinDir(dist, zipDirectoryName))
        io.copyDirectory(zipDirectory, tmpDir.toFile())

        // Delete the whole dist directory and recreate it
        File(dist).let { distFile ->
            io.rmDir(distFile)
            io.mkdir(distFile)

            // Copy the files back
            tmpDir.toFile().absolutePath.let { fromPath ->
                io.moveFile(File(KFiles.joinDir(fromPath, zipFileName)), distFile)
                io.copyDirectory(File(KFiles.joinDir(fromPath, zipDirectoryName)), distFile)
            }
        }
    }
}
