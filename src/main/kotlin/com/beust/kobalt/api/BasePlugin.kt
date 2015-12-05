package com.beust.kobalt.api

import com.beust.kobalt.Plugins
import com.beust.kobalt.TaskResult
import com.beust.kobalt.Variant
import com.beust.kobalt.internal.TaskManager
import kotlin.properties.Delegates

abstract public class BasePlugin : IPlugin {
    override var taskManager: TaskManager by Delegates.notNull()
    override fun accept(project: Project) = true
    var plugins: Plugins by Delegates.notNull()

    var context: KobaltContext by Delegates.notNull()

    override fun apply(project: Project, context: KobaltContext) {
        this.context = context
    }

    protected val projects = arrayListOf<ProjectDescription>()

    fun addProject(project: Project, dependsOn: Array<out Project>) {
        projects.add(ProjectDescription(project, dependsOn.toList()))
    }

    /**
     * Register dynamic tasks corresponding to the variants found in the project,e.g. assembleDevDebug,
     * assembleDevRelease, etc...
     */
    protected fun addVariantTasks(project: Project, taskName: String,
            runBefore : List<String> = emptyList(),
            runAfter : List<String> = emptyList(),
            runTask: (Project) -> TaskResult) {
        Variant.allVariants(project).forEach { variant ->
            val variantTaskName = variant.toTask(taskName)
            taskManager.addTask(this, project, variantTaskName, variantTaskName,
                runBefore = runBefore.map { variant.toTask(it) },
                runAfter = runAfter.map { variant.toTask(it) },
                task = { p: Project ->
                    context.variant = variant
                    runTask(project)
                })
        }
    }

}
