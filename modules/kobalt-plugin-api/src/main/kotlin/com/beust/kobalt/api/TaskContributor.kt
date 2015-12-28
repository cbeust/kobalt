package com.beust.kobalt.api

import com.beust.kobalt.IncrementalTaskInfo
import com.beust.kobalt.TaskResult
import com.beust.kobalt.Variant
import com.beust.kobalt.internal.IncrementalManager
import com.google.inject.Inject

/**
 * Plug-ins that are ITaskContributor can use this class to manage their collection of tasks and
 * implement the interface by delegating to an instance of this class (if injection permits).
 */
class TaskContributor @Inject constructor(val incrementalManager: IncrementalManager) : ITaskContributor {
    val dynamicTasks = arrayListOf<DynamicTask>()

    /**
     * Register dynamic tasks corresponding to the variants found in the project,e.g. assembleDevDebug,
     * assembleDevRelease, etc...
     *
     * TODO: this should be done automatically so that users don't have to invoke it themselves.
     * Certain tasks could have a boolean flag "hasVariants" and any task that depends on it automatically
     * depends on variants of that task.
     */
    fun addVariantTasks(plugin: IPlugin, project: Project, context: KobaltContext, taskName: String,
                        runBefore: List<String> = emptyList(),
                        runAfter: List<String> = emptyList(),
                        alwaysRunAfter: List<String> = emptyList(),
                        runTask: (Project) -> TaskResult) {
        Variant.allVariants(project).forEach { variant ->
            val variantTaskName = variant.toTask(taskName)
            dynamicTasks.add(DynamicTask(plugin, variantTaskName, variantTaskName,
                    runBefore = runBefore.map { variant.toTask(it) },
                    runAfter = runAfter.map { variant.toTask(it) },
                    alwaysRunAfter = alwaysRunAfter.map { variant.toTask(it) },
                    closure = { p: Project ->
                        context.variant = variant
                        runTask(project)
                    }))
        }
    }

    fun addIncrementalVariantTasks(plugin: IPlugin, project: Project, context: KobaltContext, taskName: String,
                                   runBefore: List<String> = emptyList(),
                                   runAfter: List<String> = emptyList(),
                                   alwaysRunAfter: List<String> = emptyList(),
                                   runTask: (Project) -> IncrementalTaskInfo) {
        Variant.allVariants(project).forEach { variant ->
            val variantTaskName = variant.toTask(taskName)
            dynamicTasks.add(DynamicTask(plugin, variantTaskName, variantTaskName,
                    runBefore = runBefore.map { variant.toTask(it) },
                    runAfter = runAfter.map { variant.toTask(it) },
                    alwaysRunAfter = alwaysRunAfter.map { variant.toTask(it) },
                    closure = incrementalManager.toIncrementalTaskClosure(plugin, taskName, runTask)))
        }
    }

    override fun tasksFor(context: KobaltContext): List<DynamicTask> = dynamicTasks
}
