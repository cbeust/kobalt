package com.beust.kobalt.internal

import com.beust.kobalt.api.ConfigPlugin
import com.beust.kobalt.api.ICompilerFlagContributor
import com.beust.kobalt.api.Project

/**
 * Base class for JVM language plug-ins.
 */
abstract class BaseJvmPlugin<T> : ConfigPlugin<T>(), ICompilerFlagContributor {
    companion object {
        // Run before other flag contributors
        val FLAG_CONTRIBUTOR_PRIORITY = ICompilerFlagContributor.DEFAULT_FLAG_PRIORITY - 10
    }

    protected fun maybeCompilerArgs(project: Project, args: List<String>)
        = if (accept(project)) args else emptyList()

    override val flagPriority = FLAG_CONTRIBUTOR_PRIORITY

}