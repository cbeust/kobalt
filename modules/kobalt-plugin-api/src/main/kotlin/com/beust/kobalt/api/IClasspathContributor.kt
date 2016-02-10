package com.beust.kobalt.api

/**
 * Plugins that export classpath entries need to implement this interface.
 */
interface IClasspathContributor : IContributor {
    fun classpathEntriesFor(project: Project?, context: KobaltContext) : Collection<IClasspathDependency>
}


