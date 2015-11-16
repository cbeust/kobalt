package com.beust.kobalt.api

import com.beust.kobalt.Plugins
import com.beust.kobalt.TaskResult
import com.beust.kobalt.Variant
import com.beust.kobalt.internal.TaskManager
import java.util.*
import kotlin.properties.Delegates

abstract public class BasePlugin : IPlugin {
    override val tasks: ArrayList<PluginTask> = arrayListOf()
    override var taskManager: TaskManager by Delegates.notNull()
    override var methodTasks = arrayListOf<IPlugin.MethodTask>()
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
    protected fun addVariantTasks(project: Project, taskName: String, runAfter : List<String>,
            runTask: (Project) -> TaskResult) {
        project.productFlavors.keys.forEach {
            val pf = project.productFlavors.get(it)
            project.buildTypes.keys.forEach { btName ->
                val bt = project.buildTypes[btName]
                val variant = Variant(pf, bt)
                val taskName = variant.toTask(taskName)
                addTask(project, taskName, taskName,
                        runAfter = runAfter.map { variant.toTask(it) },
                        task = { p: Project ->
                            context.variant = Variant(pf, bt)
                            runTask(project)
                            TaskResult()
                        })
            }
        }
    }

}
