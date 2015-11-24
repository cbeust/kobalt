package com.beust.kobalt.api

import com.beust.kobalt.TaskResult

/**
 * Plugins that can implement the "task" run should implement this interface.
 */
interface IRunContributor : IContributor {
    companion object {
        const val DEFAULT_POSITIVE_AFFINITY = 100
    }

    /**
     * @return an integer indicating your affinity for running the current project. The runner with
     * the highest affinity is selected to run it.
     */
    fun runAffinity(project: Project, context: KobaltContext) : Int

    /**
     * Run the project.
     */
    fun run(project: Project, context: KobaltContext) : TaskResult
}


