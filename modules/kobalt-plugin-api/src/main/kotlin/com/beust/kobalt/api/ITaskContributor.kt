package com.beust.kobalt.api

import com.beust.kobalt.TaskResult
import com.beust.kobalt.internal.TaskResult2

/**
 * Plug-ins that need to add dynamic tasks (tasks that are not methods annotated with @Task) need
 * to implement this interface.
 */
interface ITaskContributor : IContributor {
    fun tasksFor(project: Project, context: KobaltContext) : List<DynamicTask>
}

class DynamicTask(override val plugin: IPlugin, override val name: String, override val doc: String,
        override val group: String,
        override val project: Project,
        val dependsOn: List<String> = listOf<String>(),
        val reverseDependsOn: List<String> = listOf<String>(),
        val runBefore: List<String> = listOf<String>(),
        val runAfter: List<String> = listOf<String>(),
        val alwaysRunAfter: List<String> = listOf<String>(),
        val closure: (Project) -> TaskResult) : ITask {

    override fun call(): TaskResult2<ITask> {
        val taskResult = closure.invoke(project)
        return TaskResult2(taskResult.success, taskResult.errorMessage, this)
    }

    override fun toString() = "[DynamicTask $name dependsOn=$dependsOn reverseDependsOn=$reverseDependsOn]"
}

