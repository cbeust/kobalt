package com.beust.kobalt.api

import java.io.File

/**
 * Plug-ins can alter the source directories by implementing this interface.
 */
interface ISourceDirectoryIncerceptor : IInterceptor {
    fun intercept(project: Project, context: KobaltContext, sourceDirectories: List<File>): List<File>
}

