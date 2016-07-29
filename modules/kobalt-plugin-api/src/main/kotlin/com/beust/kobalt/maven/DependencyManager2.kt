package com.beust.kobalt.maven

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.*
import com.beust.kobalt.internal.DynamicGraph
import com.beust.kobalt.maven.aether.KobaltAether
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KFiles
import com.google.inject.Inject
import java.io.File

class DependencyManager2 @Inject constructor(val aether: KobaltAether) {
    /**
     * Create an IClasspathDependency from a Maven id.
     */
    fun createMaven(id: String) : IClasspathDependency = aether.create(id)

    /**
     * Create an IClasspathDependency from a path.
     */
    fun createFile(path: String) : IClasspathDependency = FileDependency(path)

    /**
     * Parse the id and return the correct IClasspathDependency
     */
    fun create(id: String, projectDirectory: String?) : IClasspathDependency {
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
     * Resolve the dependencies for the give project based on the scope filters.
     */
    fun resolve(project: Project, context: KobaltContext, isTest: Boolean,
            passedScopeFilters : List<Scope> = emptyList(),
            passedIds: List<IClasspathDependency> = emptyList()): List<IClasspathDependency> {
        val result = hashSetOf<IClasspathDependency>()
        val nonMavenDependencies = hashSetOf<IClasspathDependency>()

        val scopeFilters =
            if (passedScopeFilters.isEmpty())
                if (isTest) listOf(Scope.TEST)
                else listOf(Scope.COMPILE)
            else passedScopeFilters

        val toDependencies = Scope.toDependencyLambda(scopeFilters)

        // Make sure that classes/ and test-classes/ are always at the top of this classpath,
        // so that older versions of that project on the classpath don't shadow them
        if (isTest) {
            result.add(FileDependency(KFiles.makeOutputDir(project).path))
            result.add(FileDependency(KFiles.makeOutputTestDir(project).path))
        }

        // Passed and direct ids
        val ids = hashSetOf<IClasspathDependency>().apply {
            addAll(passedIds)
            addAll(toDependencies(project))
        }

        // Contributed id's
        val contributedIds = runClasspathContributors(project, context)
        contributedIds.forEach {
            if (it.isMaven) ids.add(it)
            else nonMavenDependencies.add(it)
        }

        // Dependent project id's
        val dependentIds = dependentProjectDependencies(project, context, toDependencies)
        dependentIds.forEach {
            if (it.isMaven) ids.add(it)
            else nonMavenDependencies.add(it)
        }

        //
        // Now we have all the id's, resolve them
        //
        var i = 0
        ids.forEach {
            val resolved = aether.resolveAll(it.id, filterScopes = scopeFilters)
                .map { create(it.toString(), project.directory) }
            i++
            result.addAll(resolved)
        }

        result.addAll(nonMavenDependencies)

        return result.toList()
    }

    private fun runClasspathContributors(project: Project?, context: KobaltContext) :
            Set<IClasspathDependency> {
        val result = hashSetOf<IClasspathDependency>()
        context.pluginInfo.classpathContributors.forEach { it: IClasspathContributor ->
            result.addAll(it.classpathEntriesFor(project, context))
        }
        return result
    }

    /**
     * If this project depends on other projects, we need to include their jar file and also
     * their own dependencies
     */
    private fun dependentProjectDependencies(project: Project?, context: KobaltContext,
            toDependencies: (Project) -> List<IClasspathDependency>)
            : List<IClasspathDependency> {
        // Get the transitive closure of all the projects this project depends on
        val transitiveProjects =
            if (project == null) emptyList()
            else DynamicGraph.transitiveClosure(project) { project -> project.dependsOn }

        val result = arrayListOf<IClasspathDependency>()

        /**
         * Add the class directories of projects depended upon
         */
        fun maybeAddClassDir(classDir: String) {
            // A project is allowed not to have any kobaltBuild/classes or test-classes directory if it doesn't have
            // any sources
            if (File(classDir).exists()) {
                result.add(FileDependency(classDir))
            }
        }
        transitiveProjects.forEach { p ->
            maybeAddClassDir(KFiles.joinDir(p.directory, p.classesDir(context)))
            maybeAddClassDir(KFiles.makeOutputTestDir(p).path)
        }

        // And add all the transitive projects
        result.addAll(transitiveProjects.flatMap { toDependencies(it) })
        return result
    }

}
