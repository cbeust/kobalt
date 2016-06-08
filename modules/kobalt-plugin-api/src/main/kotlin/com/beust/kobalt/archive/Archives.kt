package com.beust.kobalt.archive

import com.beust.kobalt.Features
import com.beust.kobalt.IFileSpec
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.misc.From
import com.beust.kobalt.misc.IncludedFile
import com.beust.kobalt.misc.JarUtils
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.To
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*
import java.util.zip.ZipOutputStream

class Archives {
    companion object {
        @ExportedProjectProperty(doc = "The name of the jar file", type = "String")
        const val JAR_NAME = "jarName"

        private val DEFAULT_STREAM_FACTORY = { os : OutputStream -> ZipOutputStream(os) }

        fun generateArchive(project: Project,
                context: KobaltContext,
                archiveName: String?,
                suffix: String,
                includedFiles: List<IncludedFile>,
                expandJarFiles : Boolean = false,
                outputStreamFactory: (OutputStream) -> ZipOutputStream = DEFAULT_STREAM_FACTORY) : File {
            val fullArchiveName = context.variant.archiveName(project, archiveName, suffix)
            val archiveDir = File(KFiles.libsDir(project))
            val result = File(archiveDir.path, fullArchiveName)
            log(3, "Creating $result")
            if (! Features.USE_TIMESTAMPS || isOutdated(project.directory, includedFiles, result)) {
                try {
                    outputStreamFactory(FileOutputStream(result)).use {
                        JarUtils.addFiles(project.directory, includedFiles, it, expandJarFiles)
                        log(2, text = "Added ${includedFiles.size} files to $result")
                        log(1, "  Created $result")
                    }
                } catch (e: Throwable) {
                    // make sure that incomplete archive is deleted
                    // otherwise incremental build does not work on next run
                    result.delete()
                    throw e
                }

            } else {
                log(3, "  $result is up to date")
            }

            project.projectProperties.put(JAR_NAME, result.absolutePath)

            return result
        }

        private fun isOutdated(directory: String, includedFiles: List<IncludedFile>, output: File): Boolean {
            if (! output.exists()) return true

            val lastModified = output.lastModified()
            includedFiles.forEach { root ->
                val allFiles = root.allFromFiles(directory)
                allFiles.forEach { relFile ->
                    val file = if (relFile.isAbsolute)
                        relFile // e.g. jar file or classes folder (of another project) when building a fat jar
                    else
                        File(KFiles.joinDir(directory, root.from, relFile.path))
                    if (file.isFile) {
                        if (file.lastModified() > lastModified) {
                            log(3, "    TS - Outdated $file and $output "
                                    + Date(file.lastModified()) + " " + Date(output.lastModified()))
                            return true
                        }
                    } else if (file.isDirectory) {
                        // e.g. classes folder (of another project) when building a fat jar
                        val includedFile = IncludedFile(From(""), To(""), listOf(IFileSpec.GlobSpec("**")))
                        if (isOutdated(file.absolutePath, listOf(includedFile), output))
                            return true
                    }
                }
            }
            return false
        }
    }
}
