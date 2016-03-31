package com.beust.kobalt.api

import com.beust.kobalt.internal.TaskManager

interface IPlugin : IPluginActor {
    /**
     * The name of this plug-in.
     */
    val name: String

    /**
     * @return true if this plug-in decided it should be enabled for this project.
     */
    fun accept(project: Project) : Boolean

    /**
     * Invoked on all plug-ins before the Kobalt execution stops.
     */
    fun shutdown()

    /**
     * Main entry point for a plug-in to initialize itself based on a project and a context.
     */
    fun apply(project: Project, context: KobaltContext) {}

    /**
     * Injected by Kobalt to manage tasks.
     */
    var taskManager : TaskManager
}
