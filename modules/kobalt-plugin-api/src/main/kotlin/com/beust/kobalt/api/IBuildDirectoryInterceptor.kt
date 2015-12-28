package com.beust.kobalt.api

/**
 * Plug-ins can alter the build directory by implementing this interface.
 */
interface IBuildDirectoryIncerceptor : IInterceptor {
    fun intercept(project: Project, context: KobaltContext, buildDirectory: String): String
}

