package com.beust.kobalt.api

import java.io.OutputStream

/**
 * Plugins that want to participate in the --init process (they can generate files to initialize
 * a new project).
 */
interface IInitContributor<T> : ISimpleAffinity<T> {
    /**
     * Generate the Build.kt file into the given OutputStream.
     */
    fun generateBuildFile(os: OutputStream)
}

