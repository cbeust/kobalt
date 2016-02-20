package com.beust.kobalt.api

import com.beust.kobalt.TaskResult

/**
 * Plugins that can run a project (task "run" or "test") should implement this interface.
 */
interface ITestRunnerContributor : IContributor, IProjectAffinity {
    /**
     * Run the tests. If [[configName]] is not empty, a specific test configuration is requested.
     */
    fun run(project: Project, context: KobaltContext, configName: String,
            classpath: List<IClasspathDependency>) : TaskResult
}

