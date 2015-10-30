package com.beust.kobalt.internal

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.IClasspathDependency
import com.google.inject.Inject

class JvmCompiler @Inject constructor(val dependencyManager: DependencyManager) {
    fun doCompile(project: Project?, context: KobaltContext?, action: ICompilerAction, info: CompilerActionInfo)
            : TaskResult {
        val allDependencies = arrayListOf<IClasspathDependency>()
        allDependencies.addAll(info.dependencies)
        allDependencies.addAll(calculateDependencies(project, context, info.dependencies))
        JvmCompilerPlugin.validateClasspath(allDependencies.map { it.jarFile.get().absolutePath })
        return action.compile(info.copy(dependencies = allDependencies))
    }

    /**
     * @return the classpath for this project, including the IClasspathContributors.
     */
    fun calculateDependencies(project: Project?, context: KobaltContext?,
            vararg allDependencies: List<IClasspathDependency>): List<IClasspathDependency> {
        var result = arrayListOf<IClasspathDependency>()
        allDependencies.forEach { dependencies ->
            result.addAll(dependencyManager.transitiveClosure(dependencies))
        }
        if (project != null) {
            result.addAll(runClasspathContributors(context, project))
        }

        return result
    }

    private fun runClasspathContributors(context: KobaltContext?, project: Project) :
            Collection<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        context?.classpathContributors?.forEach {
            result.addAll(it.entriesFor(project))
        }
        return result
    }

}

data class CompilerActionInfo(val dependencies: List<IClasspathDependency>,
        val sourceFiles: List<String>, val outputDir: String, val compilerArgs: List<String>)

interface ICompilerAction {
    fun compile(info: CompilerActionInfo): TaskResult
}