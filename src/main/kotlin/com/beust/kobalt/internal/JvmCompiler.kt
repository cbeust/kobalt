package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.IClasspathDependency
import com.google.inject.Inject
import java.io.File
import java.util.*

/**
 * Abstract the compilation process by running an ICompilerAction parameterized  by a CompilerActionInfo.
 * Also validates the classpath and run all the contributors.
 */
class JvmCompiler @Inject constructor(val dependencyManager: DependencyManager) {

    /**
     * Take the given CompilerActionInfo and enrich it with all the applicable contributors and
     * then pass it to the ICompilerAction.
     */
    fun doCompile(project: Project?, context: KobaltContext?, action: ICompilerAction, info: CompilerActionInfo)
            : TaskResult {

        // Dependencies
        val allDependencies = (info.dependencies
                + dependencyManager.calculateDependencies(project, context!!, info.dependencies))
            .distinct()

        // Plugins that add flags to the compiler
        val addedFlags = ArrayList(info.compilerArgs) +
            if (project != null) {
                context.pluginInfo.compilerFlagContributors.flatMap {
                    it.flagsFor(project)
                }
            } else {
                emptyList()
            }

        validateClasspath(allDependencies.map { it.jarFile.get().absolutePath })
        return action.compile(info.copy(dependencies = allDependencies, compilerArgs = addedFlags))
    }

    private fun validateClasspath(cp: List<String>) {
        cp.forEach {
            if (! File(it).exists()) {
                throw KobaltException("Couldn't find $it")
            }
        }
    }
}

data class CompilerActionInfo(val directory: String?, val dependencies: List<IClasspathDependency>,
        val sourceFiles: List<String>, val outputDir: File, val compilerArgs: List<String>)

interface ICompilerAction {
    fun compile(info: CompilerActionInfo): TaskResult
}