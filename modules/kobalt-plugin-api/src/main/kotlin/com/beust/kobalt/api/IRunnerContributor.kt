package com.beust.kobalt.api

import com.beust.kobalt.TaskResult

/**
 * Plugins that can run a project (task "run" or "test") should implement this interface.
 *
 * Currently not used.
 */
interface IRunnerContributor : IContributor, IProjectAffinity {
    /**
     * Run the project.
     */
    fun run(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>) : TaskResult
}
