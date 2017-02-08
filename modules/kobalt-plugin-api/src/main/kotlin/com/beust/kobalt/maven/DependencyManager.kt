package com.beust.kobalt.maven

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.*
import com.beust.kobalt.maven.aether.Filters
import com.beust.kobalt.maven.aether.KobaltMavenResolver
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.google.common.collect.ArrayListMultimap
import org.eclipse.aether.graph.DependencyFilter
import org.eclipse.aether.util.filter.OrDependencyFilter
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DependencyManager @Inject constructor(val executors: KobaltExecutors,
        val resolver: KobaltMavenResolver) : IDependencyManager {

    companion object {
        fun create(id: String, optional: Boolean = false, projectDirectory: String? = null) =
                Kobalt.INJECTOR.getInstance(DependencyManager::class.java).create(id, optional, projectDirectory)
    }

    /**
     * Parse the id and return the correct IClasspathDependency
     */
    override fun create(id: String, optional: Boolean, projectDirectory: String?) : IClasspathDependency {
        if (id.startsWith(FileDependency.PREFIX_FILE)) {
            val path = if (projectDirectory != null) {
                val idPath = id.substring(FileDependency.PREFIX_FILE.length)
                if (! File(idPath).isAbsolute) {
                    // If the project directory is relative, we might not be in the correct directory to locate
                    // that file, so we'll use the absolute directory deduced from the build file path. Pick
                    // the first one that produces an actual file
                    val result = listOf(File(projectDirectory), Kobalt.context?.internalContext?.absoluteDir).map {
                        File(it, idPath)
                    }.firstOrNull {
                        it.exists()
                    }
                    result ?: throw KobaltException("Couldn't find $id")

                } else {
                    File(idPath)
                }
            } else {
                File(id.substring(FileDependency.PREFIX_FILE.length))
            }
            return createFile(path.path)
        } else {
            // Convert to a Kobalt id first (so that if it doesn't have a version, it gets translated to
            // an Aether ranged id "[0,)")
            return createMaven(MavenId.create(id).toId, optional)
        }
    }

    /**
     * Create an IClasspathDependency from a Maven id.
     */
    override fun createMaven(id: String, optional: Boolean) : IClasspathDependency=
        if (KobaltMavenResolver.isRangeVersion(id)) {
            Kobalt.INJECTOR.getInstance(DependencyManager::class.java).create(id, optional)
        } else {
            resolver.create(id, optional)
        }

    /**
     * Create an IClasspathDependency from a path.
     */
    override fun createFile(path: String) : IClasspathDependency = FileDependency(path)

    /**
     * @return the source dependencies for this project, including the contributors.
     */
    override fun dependencies(project: Project, context: KobaltContext) = dependencies(project, context, false)

    /**
     * @return the test dependencies for this project, including the contributors.
     */
    override fun testDependencies(project: Project, context: KobaltContext) = dependencies(project, context, true)

    /**
     * Transitive dependencies for the compilation of this project.
     */
//    fun calculateTransitiveDependencies(project: Project, context: KobaltContext)
//        = calculateDependencies(project, context, project.dependentProjects,
//            project.compileDependencies + project.compileRuntimeDependencies)

    /**
     * @return the classpath for this project, including the IClasspathContributors.
     * allDependencies is typically either compileDependencies or testDependencies. If no dependencies
     * are passed, they are calculated from the scope filters.
     */
    override fun calculateDependencies(project: Project?, context: KobaltContext,
            dependencyFilter: DependencyFilter,
            scopes: List<Scope>,
            vararg passedDependencies: List<IClasspathDependency>): List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()

        /**
         * Extract the correct dependencies from the project based on the scope filters.
         */
        fun filtersToDependencies(project: Project, scopes: Collection<Scope>): List<IClasspathDependency> {
            val result = arrayListOf<IClasspathDependency>().apply {
                if (scopes.contains(Scope.COMPILE)) {
                    addAll(project.compileDependencies)
                }
                if (scopes.contains(Scope.RUNTIME)) {
                    addAll(project.compileRuntimeDependencies)
                }
                if (scopes.contains(Scope.TEST)) {
                    addAll(project.testDependencies)
                }
            }
            return result.filter { ! it.optional }
        }

        val allDependencies : Array<out List<IClasspathDependency>> =
            if (project == null || passedDependencies.any()) passedDependencies
            else arrayOf(filtersToDependencies(project, scopes))

        // Make sure that classes/ and test-classes/ are always at the top of this classpath,
        // so that older versions of that project on the classpath don't shadow them
        if (project != null && scopes.contains(Scope.TEST)) {
            result.add(FileDependency(KFiles.makeOutputDir(project).path))
            result.add(FileDependency(KFiles.makeOutputTestDir(project).path))
        }

        allDependencies.forEach { dependencies ->
            result.addAll(transitiveClosure(dependencies, dependencyFilter, project?.name))
        }
        result.addAll(runClasspathContributors(project, context))
        result.addAll(dependentProjectDependencies(project, context, dependencyFilter, scopes))

        // Dependencies get reordered by transitiveClosure() but since we just added a bunch of new ones,
        // we need to reorder them again in case we're adding dependencies that are already present
        // but with a different version
        val reordered = reorderDependencies(result)
        return reordered
    }

    private fun runClasspathContributors(project: Project?, context: KobaltContext) :
            Collection<IClasspathDependency> {
        val result = hashSetOf<IClasspathDependency>()
        context.pluginInfo.classpathContributors.forEach { it: IClasspathContributor ->
            result.addAll(it.classpathEntriesFor(project, context))
        }
        return result
    }

    /**
     * Return the transitive closure of the dependencies *without* running the classpath contributors.
     * TODO: This should be private, everyone should be calling calculateDependencies().
     */
    fun transitiveClosure(dependencies : List<IClasspathDependency>,
            dependencyFilter: DependencyFilter? = null,
            requiredBy: String? = null): List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        dependencies.forEach {
            result.add(it)
            if (it.isMaven) {
                val resolved = resolver.resolveToIds(it.id, null, dependencyFilter)
                result.addAll(resolved.map { create(it) })
            }
        }
        val reordered = reorderDependencies(result)
        return reordered
    }

    /**
     * Reorder dependencies so that if an artifact appears several times, only the one with the higest version
     * is included.
     */
    private fun reorderDependencies(dependencies: Collection<IClasspathDependency>): List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        val map : ArrayListMultimap<String, IClasspathDependency> = ArrayListMultimap.create()
        // The multilist maps each artifact to a list of all the versions found
        // (e.g. {org.testng:testng -> (6.9.5, 6.9.4, 6.1.1)}), then we return just the first one
        dependencies.forEach {
            map.put(it.shortId, it)
        }
        for (k in map.keySet()) {
            val l = map.get(k)
            Collections.sort(l, Collections.reverseOrder())
            result.add(l[0])
        }
        return result
    }

    /**
     * If this project depends on other projects, we need to include their jar file and also
     * their own dependencies
     */
    private fun dependentProjectDependencies(project: Project?, context: KobaltContext,
            dependencyFilter: DependencyFilter, scopes: List<Scope>): List<IClasspathDependency> {
        if (project == null) {
            return emptyList()
        } else {
            val result = arrayListOf<IClasspathDependency>()

            fun maybeAddClassDir(classDir: String) {
                // A project is allowed not to have any kobaltBuild/classes or test-classes directory if it doesn't have
                // any sources
                if (File(classDir).exists()) {
                    result.add(FileDependency(classDir))
                }
            }

            project.dependsOn.forEach { p ->
                maybeAddClassDir(KFiles.joinDir(p.directory, p.classesDir(context)))
                val isTest = scopes.contains(Scope.TEST)
                if (isTest) maybeAddClassDir(KFiles.makeOutputTestDir(project).path)
                val otherDependencies = calculateDependencies(p, context, dependencyFilter, scopes)
                result.addAll(otherDependencies)

            }
            return result
        }
    }

    private fun dependencies(project: Project, context: KobaltContext, isTest: Boolean)
            : List<IClasspathDependency> {
        val transitive = hashSetOf<IClasspathDependency>()
        with(project) {
            val scopeFilters : ArrayList<Scope> = arrayListOf(Scope.COMPILE)
            context.variant.let { variant ->
                val deps = arrayListOf(compileDependencies, compileProvidedDependencies,
                        variant.buildType.compileDependencies,
                        variant.buildType.compileProvidedDependencies,
                        variant.productFlavor.compileDependencies,
                        variant.productFlavor.compileProvidedDependencies
                )
                if (isTest) {
                    deps.add(testDependencies)
                    deps.add(testProvidedDependencies)
                    scopeFilters.add(Scope.TEST)
                }
                val filter =
                    if (isTest) OrDependencyFilter(Filters.COMPILE_FILTER, Filters.TEST_FILTER)
                    else Filters.COMPILE_FILTER
                deps.filter { it.any() }.forEach {
                    transitive.addAll(calculateDependencies(project, context, filter,
                            scopes = Scope.toScopes(isTest),
                            passedDependencies = it))
                }
            }
        }

        // Make sure that classes/ and test-classes/ are always at the top of this classpath,
        // so that older versions of that project on the classpath don't shadow them
        val result = arrayListOf<IClasspathDependency>().apply {
            if (isTest) {
                add(FileDependency(KFiles.makeOutputDir(project).path))
                add(FileDependency(KFiles.makeOutputTestDir(project).path))
            }
            addAll(reorderDependencies(transitive))
        }

        return result
    }

}
