package com.beust.kobalt.api

/**
 * Plugins that add compiler flags.
 */
class FlagContributor(val flagPriority: Int = DEFAULT_FLAG_PRIORITY,
        val closure: (project: Project, context: KobaltContext, currentFlags: List<String>,
                suffixesBeingCompiled: List<String>) -> List<String>) : IContributor {
    companion object {
        val DEFAULT_FLAG_PRIORITY = 20
    }

    fun flagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>) = closure(project, context, currentFlags, suffixesBeingCompiled)
}

interface IFlagBase {
    val flagPriority: Int get() = FlagContributor.DEFAULT_FLAG_PRIORITY
}

interface ICompilerFlagContributor : IContributor, IFlagBase {
    fun compilerFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>): List<String>
}

interface IDocFlagContributor : IContributor, IFlagBase {
    fun docFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>): List<String>
}