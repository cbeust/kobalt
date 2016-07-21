package com.beust.kobalt.api

/**
 * Manage the creation of dependencies and also provide dependencies for projects.
 */
interface IDependencyManager {
    /**
     * Parse the id and return the correct IClasspathDependency
     */
    fun create(id: String, projectDirectory: String? = null): IClasspathDependency

    /**
     * Create an IClasspathDependency from a Maven id.
     */
    fun createMaven(id: String): IClasspathDependency

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
    fun calculateDependencies(project: Project?, context: KobaltContext, isTest: Boolean = false,
            vararg allDependencies: List<IClasspathDependency>): List<IClasspathDependency>
}