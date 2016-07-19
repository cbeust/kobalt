package com.beust.kobalt.maven

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.*
import com.beust.kobalt.maven.aether.KobaltAether
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.google.common.collect.ArrayListMultimap
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DependencyManager @Inject constructor(val executors: KobaltExecutors, val aether: KobaltAether)
        : IDependencyManager {

    companion object {
        fun create(id: String, projectDirectory: String? = null) =
                Kobalt.INJECTOR.getInstance(DependencyManager::class.java).create(id, projectDirectory)
    }

    /**
     * Parse the id and return the correct IClasspathDependency
     */
    override fun create(id: String, projectDirectory: String?) : IClasspathDependency {
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
            return createMaven(MavenId.create(id).toId)
        }
    }

    /**
     * Create an IClasspathDependency from a Maven id.
     */
    override fun createMaven(id: String) : IClasspathDependency = aether.create(id)

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
     * allDependencies is typically either compileDependencies or testDependencies
     */
    override fun calculateDependencies(project: Project?, context: KobaltContext,
            vararg allDependencies: List<IClasspathDependency>): List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        allDependencies.forEach { dependencies ->
            result.addAll(transitiveClosure(dependencies, project?.name))
        }
        result.addAll(runClasspathContributors(project, context))
        result.addAll(dependentProjectDependencies(project, context))

        return result
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
    fun transitiveClosure(dependencies : List<IClasspathDependency>, requiredBy: String? = null):
            List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        dependencies.forEach {
            result.add(it)
            if (it.isMaven) {
                val resolved = aether.resolveAll(it.id).map { it.toString() }
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
    private fun dependentProjectDependencies(
            project: Project?, context: KobaltContext) : List<IClasspathDependency> {
        if (project == null) {
            return emptyList()
        } else {
            val result = arrayListOf<IClasspathDependency>()
            project.dependsOn.forEach { p ->
                KFiles.joinDir(p.directory, p.classesDir(context)).let { classDir ->
                    // A project is allowed not to have any kobaltBuild/classes directory if it doesn't have
                    // any sources
                    if (File(classDir).exists()) {
                        result.add(FileDependency(KFiles.joinDir(p.directory, p.classesDir(context))))
                    }
                }
                val otherDependencies = calculateDependencies(p, context, p.compileDependencies)
                result.addAll(otherDependencies)

            }
            return result
        }
    }

    private fun dependencies(project: Project, context: KobaltContext, isTest: Boolean)
            : List<IClasspathDependency> {
        val transitive = hashSetOf<IClasspathDependency>()
        with(project) {
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
                }
                deps.filter { it.any() }.forEach {
                    transitive.addAll(calculateDependencies(project, context, it))
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
