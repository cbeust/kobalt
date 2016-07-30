package com.beust.kobalt.api

/**
 * Plug-ins that listen to build events.
 */
interface IBuildListener : IListener {
    fun taskStart(project: Project, context: KobaltContext, taskName: String)
    fun taskEnd(project: Project, context: KobaltContext, taskName: String)
}
