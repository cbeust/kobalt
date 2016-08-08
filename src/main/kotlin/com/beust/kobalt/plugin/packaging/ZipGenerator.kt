package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.JarGenerator
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.archive.Archives
import com.beust.kobalt.archive.Zip
import com.beust.kobalt.internal.KobaltLog
import com.beust.kobalt.maven.DependencyManager
import com.google.inject.Inject

class ZipGenerator @Inject constructor(val dependencyManager: DependencyManager, val kobaltLog: KobaltLog) {
    fun generateZip(project: Project, context: KobaltContext, zip: Zip) {
        val allFiles = JarGenerator.findIncludedFiles(project.directory, zip.includedFiles, zip.excludes)
        Archives.generateArchive(project, context, zip.name, ".zip", allFiles, kobaltLog = kobaltLog)
    }
}
