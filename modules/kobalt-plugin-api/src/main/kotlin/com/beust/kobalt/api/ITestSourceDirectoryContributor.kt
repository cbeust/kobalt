package com.beust.kobalt.api

import java.io.File

/**
 * Plug-ins that add tets source directories to be compiled need to implement this interface.
 */
interface ITestSourceDirectoryContributor : IContributor {
    fun testSourceDirectoriesFor(project: Project, context: KobaltContext): List<File>
}

fun KobaltContext.testSourceDirectories(project: Project) : List<File> {
    val result = pluginInfo.testSourceDirContributors.flatMap {
        it.testSourceDirectoriesFor(project, this)
    }
    return result
}

