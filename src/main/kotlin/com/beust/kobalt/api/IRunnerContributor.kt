package com.beust.kobalt.api

import com.beust.kobalt.TaskResult
import com.beust.kobalt.maven.dependency.IClasspathDependency

/**
 * Plugins that can run a project (task "run" or "test") should implement this interface.
 */
interface IRunnerContributor : IContributor, IAffinity {
    /**
     * Run the project.
     */
    fun run(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>) : TaskResult
}
