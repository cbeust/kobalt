package com.beust.kobalt.api

/**
 * Plug-ins that listen to build events.
 */
interface IBuildListener : IListener {

    class TaskEndInfo(val success: Boolean, val shortMessage: String? = null,
            val longMessage: String? = null)

    fun taskStart(project: Project, context: KobaltContext, taskName: String) {}
    fun taskEnd(project: Project, context: KobaltContext, taskName: String, info: TaskEndInfo) {}

    fun projectStart(project: Project, context: KobaltContext) {}
    fun projectEnd(project: Project, context: KobaltContext, status: ProjectBuildStatus) {}
}

enum class ProjectBuildStatus {
    SUCCESS, FAILED, SKIPPED
}
