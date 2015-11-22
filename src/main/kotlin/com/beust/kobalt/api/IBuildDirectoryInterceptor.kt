package com.beust.kobalt.api

/**
 * Plug-ins can alter the build directory by implementing this interface.
 */
interface IBuildDirectoryIncerceptor : IPluginActor {
    fun intercept(project: Project, context: KobaltContext, buildDirectory: String) : String
}

