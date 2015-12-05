package com.beust.kobalt.api

import com.beust.kobalt.Plugins
import com.beust.kobalt.internal.TaskManager

abstract public class BasePlugin : IPlugin {
    lateinit var context: KobaltContext

    override fun accept(project: Project) = true

    override fun apply(project: Project, context: KobaltContext) {
        this.context = context
    }

    protected val projects = arrayListOf<ProjectDescription>()

    fun addProject(project: Project, dependsOn: Array<out Project>) =
            projects.add(ProjectDescription(project, dependsOn.toList()))

    override lateinit var taskManager: TaskManager
    lateinit var plugins: Plugins
}
