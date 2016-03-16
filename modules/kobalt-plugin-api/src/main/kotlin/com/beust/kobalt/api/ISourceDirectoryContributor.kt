package com.beust.kobalt.api

import java.io.File

/**
 * Plug-ins that add source directories to be compiled need to implement this interface.
 */
interface ISourceDirectoryContributor {
    fun sourceDirectoriesFor(project: Project, context: KobaltContext): List<File>
}

/**
 * @return the source directories for this project including source contributors.
 */
fun KobaltContext.sourceDirectories(project: Project) : Set<File> {
    val result = pluginInfo.sourceDirContributors.flatMap {
        it.sourceDirectoriesFor(project, this)
    }
    return result.toSet()
}
