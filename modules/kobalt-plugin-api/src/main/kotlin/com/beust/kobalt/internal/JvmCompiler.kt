package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.CompilerActionInfo
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
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
                + dependencyManager.calculateDependencies(project, context!!, passedDependencies = info.dependencies))
            .distinct()

        // Plugins that add flags to the compiler
        val currentFlags = arrayListOf<String>().apply { addAll(info.compilerArgs) }
        val contributorFlags : List<String> = if (project != null) {
            val contributors = context.pluginInfo.compilerFlagContributors
            contributors.sortBy { it.flagPriority }
            context.pluginInfo.compilerFlagContributors.forEach {
                currentFlags.addAll(it.flagsFor(project, context, currentFlags, info.suffixesBeingCompiled))
            }
            currentFlags
        } else {
            emptyList()
        }

        val addedFlags = contributorFlags + ArrayList(info.compilerArgs)

        validateClasspath(allDependencies.map { it.jarFile.get().absolutePath })
        return action.compile(project?.name, info.copy(dependencies = allDependencies, compilerArgs = addedFlags))
    }

    private fun validateClasspath(cp: List<String>) {
        cp.forEach {
            if (! File(it).exists()) {
                throw KobaltException("Invalid classpath: couldn't find $it")
            }
        }
    }
}

interface ICompilerAction {
    fun compile(projectName: String?, info: CompilerActionInfo): TaskResult
}