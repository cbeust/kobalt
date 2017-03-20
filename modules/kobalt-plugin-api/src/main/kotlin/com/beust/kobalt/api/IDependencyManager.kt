package com.beust.kobalt.api

import com.beust.kobalt.maven.aether.Filters.EXCLUDE_OPTIONAL_FILTER
import com.beust.kobalt.maven.aether.KobaltMavenResolver
import com.beust.kobalt.maven.aether.Scope
import org.eclipse.aether.graph.DependencyFilter
import org.eclipse.aether.graph.DependencyNode

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
    fun dependencies(project: Project, context: KobaltContext, scopes: List<Scope>): List<IClasspathDependency>

    /**
     * @return the test dependencies for this project, including the contributors.
     */
    fun testDependencies(project: Project, context: KobaltContext): List<IClasspathDependency>

    /**
     * @return the classpath for this project, including the IClasspathContributors.
     * allDependencies is typically either compileDependencies or testDependencies
     */
    fun calculateDependencies(project: Project?, context: KobaltContext,
            dependencyFilter: DependencyFilter = createDependencyFilter(project?.compileDependencies ?: emptyList()),
            scopes: List<Scope> = listOf(Scope.COMPILE),
            vararg passedDependencies: List<IClasspathDependency>): List<IClasspathDependency>

    /**
     * Create an Aether dependency filter that uses the dependency configuration included in each
     * IClasspathDependency.
     */
    fun createDependencyFilter(dependencies: List<IClasspathDependency>) : DependencyFilter {
        return DependencyFilter { p0, p1 ->
            fun isNodeExcluded(passedDep: IClasspathDependency, node: DependencyNode) : Boolean {
                val dep = create(KobaltMavenResolver.artifactToId(node.artifact))
                return passedDep.excluded.any { ex -> ex.isExcluded(dep)}
            }

            val accept = dependencies.any {
                // Is this dependency excluded?
                val isExcluded = isNodeExcluded(it, p0)

                // Is the parent dependency excluded?
                val isParentExcluded =
                    if (p1.any()) {
                        isNodeExcluded(it, p1[0])
                    } else {
                        false
                    }

                // Only accept if no exclusions were found
                ! isExcluded && ! isParentExcluded
            }

            if (! accept) {
                println("  FOUND EXCLUDED DEP: " + p0)
            }

            if (accept) EXCLUDE_OPTIONAL_FILTER.accept(p0, p1)
            else accept
        }
    }
}