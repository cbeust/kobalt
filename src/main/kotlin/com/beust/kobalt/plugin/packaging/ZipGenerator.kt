package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.google.inject.Inject

class ZipGenerator @Inject constructor(val dependencyManager: DependencyManager) {
    fun findIncludedFiles(project: Project, context: KobaltContext, zip: Zip)
            = PackagingPlugin.findIncludedFiles(project.directory, zip.includedFiles, zip.excludes)

    fun generateZip(project: Project, context: KobaltContext, zip: Zip) {
        val allFiles = findIncludedFiles(project, context, zip)
        PackagingPlugin.generateArchive(project, context, zip.name, ".zip", allFiles)
    }
}
