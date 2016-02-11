package com.beust.kobalt.api

import java.io.File

/**
 * Plug-ins that add source directories to be compiled need to implement this interface.
 */
interface ISourceDirectoryContributor {
    fun sourceDirectoriesFor(project: Project, context: KobaltContext): List<File>
}

fun KobaltContext.sourceDirectories(project: Project) : List<File> {
    val result = pluginInfo.sourceDirContributors.flatMap {
        it.sourceDirectoriesFor(project, this)
    }
    return result
}
