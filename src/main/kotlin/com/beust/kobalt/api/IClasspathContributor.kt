package com.beust.kobalt.api

import com.beust.kobalt.maven.IClasspathDependency

/**
 * Implement this interface in order to add your own entries to the classpath. A list of contributors
 * can be found on the `KobaltContext`.
 */
interface IClasspathContributor {
    fun entriesFor(project: Project) : Collection<IClasspathDependency>
}
