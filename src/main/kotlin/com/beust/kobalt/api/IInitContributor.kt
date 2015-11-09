package com.beust.kobalt.api

import java.io.File
import java.io.OutputStream

/**
 * Plugins that want to participate in the --init process (they can generate files to initialize
 * a new project).
 */
interface IInitContributor : IContributor {
    /**
     * How many files your plug-in understands in the given directory. The contributor with the
     * highest number will be asked to generate the build file.
     */
    fun filesManaged(dir: File): Int

    /**
     * Generate the Build.kt file into the given OutputStream.
     */
    fun generateBuildFile(os: OutputStream)
}

