package com.beust.kobalt.internal

import com.beust.kobalt.api.ConfigPlugin
import com.beust.kobalt.api.ICompilerFlagContributor

/**
 * Base class for JVM language plug-ins.
 */
abstract class BaseJvmPlugin<T> : ConfigPlugin<T>(), ICompilerFlagContributor {
    companion object {
        // Run before other flag contributors
        val FLAG_CONTRIBUTOR_PRIORITY = ICompilerFlagContributor.DEFAULT_FLAG_PRIORITY - 10
    }

    protected fun maybeCompilerArgs(sourceSuffixes: List<String>, suffixesBeingCompiled: List<String>,
            args: List<String>)
        = if (sourceSuffixes.any { suffixesBeingCompiled.contains(it) }) args else emptyList()

    override val flagPriority = FLAG_CONTRIBUTOR_PRIORITY

}