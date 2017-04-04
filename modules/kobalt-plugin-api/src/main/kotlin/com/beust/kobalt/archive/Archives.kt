package com.beust.kobalt.archive

import com.beust.kobalt.*
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.misc.JarUtils
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*
import java.util.zip.ZipOutputStream

class Archives {
    companion object {
        @ExportedProjectProperty(doc = "The name of the jar file", type = "String")
        const val JAR_NAME = "jarName"
        @ExportedProjectProperty(doc = "The name of the a jar file with a main() method", type = "String")
        const val JAR_NAME_WITH_MAIN_CLASS = "jarNameWithMainClass"

        private val DEFAULT_STREAM_FACTORY = { os : OutputStream -> ZipOutputStream(os) }

        fun defaultArchiveName(project: Project) = project.name + "-" + project.version

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
            context.logger.log(project.name, 3, "Creating $result")
            if (! Features.USE_TIMESTAMPS || isOutdated(project.directory, includedFiles, result)) {
                try {
                    outputStreamFactory(FileOutputStream(result)).use {
                        JarUtils.addFiles(project.directory, includedFiles, it, expandJarFiles)
                        context.logger.log(project.name, 2, "Added ${includedFiles.size} files to $result")
                        context.logger.log(project.name, 1, "  Created $result")
                    }
                } catch (e: Throwable) {
                    // make sure that incomplete archive is deleted
                    // otherwise incremental build does not work on next run
                    result.delete()
                    throw e
                }

            } else {
                context.logger.log(project.name, 3, "  $result is up to date")
            }

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
                            kobaltLog(3, "    TS - Outdated $file and $output "
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
