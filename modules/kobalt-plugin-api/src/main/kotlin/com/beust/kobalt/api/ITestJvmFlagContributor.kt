package com.beust.kobalt.api

/**
 * Plug-ins that add flags to the JVM used to run tests should implement this interface.
 */
interface ITestJvmFlagContributor : IContributor {
    fun testJvmFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>) : List<String>
}
