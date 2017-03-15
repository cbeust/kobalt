package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.ArchiveGenerator
import com.beust.kobalt.JarGenerator
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.archive.Archives
import com.beust.kobalt.archive.Zip
import com.beust.kobalt.internal.ParallelLogger
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.IncludedFile
import com.google.inject.Inject

class ZipGenerator @Inject constructor(val dependencyManager: DependencyManager, val kobaltLog: ParallelLogger)
        : ArchiveGenerator {

    override val suffix = ".zip"

    override fun findIncludedFiles(project: Project, context: KobaltContext, zip: Zip): List<IncludedFile> {
        return JarGenerator.findIncludedFiles(project.directory, zip.includedFiles, zip.excludes)
    }

    override fun generateArchive(project: Project, context: KobaltContext, zip: Zip, files: List<IncludedFile>)
        = Archives.generateArchive(project, context, zip.name, ".zip", files)
}
