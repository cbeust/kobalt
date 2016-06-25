package com.beust.kobalt.internal

import com.beust.kobalt.api.*
import com.beust.kobalt.misc.KFiles

/**
 * Base class for JVM language plug-ins.
 */
abstract class BaseJvmPlugin<T>(open val configActor: ConfigActor<T>) :
        BasePlugin(),
        IConfigActor<T> by configActor,
        ICompilerFlagContributor {
    companion object {
        // Run before other flag contributors
        val FLAG_CONTRIBUTOR_PRIORITY = ICompilerFlagContributor.DEFAULT_FLAG_PRIORITY - 10
    }

    protected fun maybeCompilerArgs(sourceSuffixes: List<String>, suffixesBeingCompiled: List<String>,
            args: List<String>)
        = if (sourceSuffixes.any { suffixesBeingCompiled.contains(it) }) args else emptyList()

    override val flagPriority = FLAG_CONTRIBUTOR_PRIORITY

    override fun accept(project: Project) = hasSourceFiles(project)

    // IBuildConfigContributor

    private fun hasSourceFiles(project: Project)
            = KFiles.findSourceFiles(project.directory, project.sourceDirectories, sourceSuffixes()).size > 0

    fun affinity(project: Project) = if (hasSourceFiles(project)) 1 else 0

    abstract fun sourceSuffixes() : List<String>
}