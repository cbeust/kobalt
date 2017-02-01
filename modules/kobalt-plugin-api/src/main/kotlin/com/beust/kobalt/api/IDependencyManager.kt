package com.beust.kobalt.api

import com.beust.kobalt.maven.aether.Filters
import com.beust.kobalt.maven.aether.Scope
import org.eclipse.aether.graph.DependencyFilter

/**
 * Manage the creation of dependencies and also provide dependencies for projects.
 */
interface IDependencyManager {
    /**
     * Parse the id and return the correct IClasspathDependency
     */
    fun create(id: String, optional: Boolean = false, projectDirectory: String? = null): IClasspathDependency

    /**
     * Create an IClasspathDependency from a Maven id.
     */
    fun createMaven(id: String, optional: Boolean = false): IClasspathDependency

    /**
     * Create an IClasspathDependency from a path.
     */
    fun createFile(path: String): IClasspathDependency

    /**
     * @return the source dependencies for this project, including the contributors.
     */
    fun dependencies(project: Project, context: KobaltContext): List<IClasspathDependency>

    /**
     * @return the test dependencies for this project, including the contributors.
     */
    fun testDependencies(project: Project, context: KobaltContext): List<IClasspathDependency>

    /**
     * @return the classpath for this project, including the IClasspathContributors.
     * allDependencies is typically either compileDependencies or testDependencies
     */
    fun calculateDependencies(project: Project?, context: KobaltContext,
            dependencyFilter: DependencyFilter = Filters.EXCLUDE_OPTIONAL_FILTER,
            scopes: List<Scope> = listOf(Scope.COMPILE),
            vararg passedDependencies: List<IClasspathDependency>): List<IClasspathDependency>
}