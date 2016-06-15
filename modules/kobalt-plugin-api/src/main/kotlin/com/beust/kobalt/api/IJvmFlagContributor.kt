package com.beust.kobalt.api

/**
 * Plug-ins that add flags to the JVM used to run apps  should implement this interface.
 */
interface IJvmFlagContributor : IContributor {
    /**
     * The list of JVM flags that will be added to the JVM when the app gets run. @param[currentFlags] is only here
     * for convenience, in case you need to look at the current JVM flags before adding your own flags.
     */
    fun jvmFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>) : List<String>
}

