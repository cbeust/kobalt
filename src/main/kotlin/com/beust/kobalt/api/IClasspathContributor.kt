package com.beust.kobalt.api

import com.beust.kobalt.maven.dependency.IClasspathDependency

/**
 * Plugins that export classpath entries need to implement this interface.
 */
interface IClasspathContributor : IContributor {
    fun entriesFor(project: Project?) : Collection<IClasspathDependency>
}


