package com.beust.kobalt.api

import com.beust.kobalt.maven.IClasspathDependency

/**
 * Plugins that export classpath entries need to implement this interface.
 */
interface IClasspathContributor {
    fun entriesFor(project: Project?) : Collection<IClasspathDependency>
}


