package com.beust.kobalt.api

/**
 * Plug-ins that add flags to the JVM used to run tests should implement this interface.
 */
interface ITestJvmFlagInterceptor : IInterceptor {
    /**
     * @return the list of all flags that should be used. If you only want to add flags to the current list,
     * just return the concatenation of @param[currentFlags] and your own list (or use ITestJvmFlagContributor).
     * If you actually alter the list of flags, make sure you don't remove anything critical from @param[currentFlags].
     */
    fun testJvmFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>) : List<String>
}

