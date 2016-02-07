package com.beust.kobalt.api

/**
 * Plugins that add compiler flags.
 */
interface ICompilerFlagContributor : IContributor {
    fun flagsFor(project: Project, context: KobaltContext, currentFlags: List<String>): List<String>
    val flagPriority: Int
        get() = DEFAULT_FLAG_PRIORITY

    companion object {
        val DEFAULT_FLAG_PRIORITY = 20
    }
}
