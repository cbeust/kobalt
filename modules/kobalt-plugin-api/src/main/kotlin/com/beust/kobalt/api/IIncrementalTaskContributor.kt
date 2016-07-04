package com.beust.kobalt.api

import com.beust.kobalt.IncrementalTaskInfo

/**
 * Plug-ins that need to add incremental dynamic tasks (tasks that are not methods annotated with @Task) need
 * to implement this interface.
 */
interface IIncrementalTaskContributor : IContributor {
    fun incrementalTasksFor(project: Project, context: KobaltContext) : List<IncrementalDynamicTask>
}

class IncrementalDynamicTask(val context: KobaltContext,
        val plugin: IPlugin,
        val name: String,
        val doc: String,
        val group: String,
        val project: Project,
        val dependsOn: List<String> = listOf<String>(),
        val reverseDependsOn: List<String> = listOf<String>(),
        val runBefore: List<String> = listOf<String>(),
        val runAfter: List<String> = listOf<String>(),
        val alwaysRunAfter: List<String> = listOf<String>(),
        val incrementalClosure: (Project) -> IncrementalTaskInfo) {
    override fun toString() = "[IncrementalDynamicTask $name dependsOn=$dependsOn reverseDependsOn=$reverseDependsOn]"
}


