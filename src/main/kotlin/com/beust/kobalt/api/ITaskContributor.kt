package com.beust.kobalt.api

import com.beust.kobalt.TaskResult

/**
 * Plug-ins that need to add dynamic tasks (tasks that are not methods annotated with @Task) need
 * to implement this interface.
 */
interface ITaskContributor : IContributor {
    fun tasksFor(context: KobaltContext) : List<DynamicTask>
}

class DynamicTask(val name: String, val description: String = "",
        val runBefore: List<String> = listOf<String>(),
        val runAfter: List<String> = listOf<String>(),
        val alwaysRunAfter: List<String> = listOf<String>(),
        val closure: (Project) -> TaskResult)
