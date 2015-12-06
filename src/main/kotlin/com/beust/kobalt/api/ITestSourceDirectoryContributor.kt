package com.beust.kobalt.api

import java.io.File

/**
 * Plug-ins that add tets source directories to be compiled need to implement this interface.
 */
interface ITestSourceDirectoryContributor {
    fun testSourceDirectoriesFor(project: Project, context: KobaltContext): List<File>
}

