package com.beust.kobalt

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.archive.Zip
import com.beust.kobalt.misc.KFiles
import java.io.File

interface ArchiveGenerator {
    fun findIncludedFiles(project: Project, context: KobaltContext, zip: Zip) : List<IncludedFile>
    val suffix: String
    fun generateArchive(project: Project, context: KobaltContext, zip: Zip, files: List<IncludedFile>) : File

    fun fullArchiveName(project: Project, context: KobaltContext, archiveName: String?) : File {
        val fullArchiveName = context.variant.archiveName(project, archiveName, suffix)
        val archiveDir = File(KFiles.libsDir(project))
        val result = File(archiveDir.path, fullArchiveName)
        return result
    }

}
