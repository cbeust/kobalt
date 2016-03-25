package com.beust.kobalt.maven

import com.beust.kobalt.api.*
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.google.common.collect.ArrayListMultimap
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DependencyManager @Inject constructor(val executors: KobaltExecutors, val depFactory: DepFactory) {

    /**
     * Create an IClasspathDependency from a Maven id.
     */
    fun createMaven(id: String) : IClasspathDependency = depFactory.create(id)

    /**
     * Create an IClasspathDependency from a path.
     */
    fun createFile(path: String) : IClasspathDependency = FileDependency(path)

    /**
     * @return the source dependencies for this project, including the contributors.
     */
    fun dependencies(project: Project, context: KobaltContext) = dependencies(project, context, false)

    /**
     * @return the test dependencies for this project, including the contributors.
     */
    fun testDependencies(project: Project, context: KobaltContext) = dependencies(project, context, true)

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
    fun calculateDependencies(project: Project?, context: KobaltContext,
            dependentProjects: List<ProjectDescription> = emptyList(),
            vararg allDependencies: List<IClasspathDependency>): List<IClasspathDependency> {
        var result = arrayListOf<IClasspathDependency>()
        allDependencies.forEach { dependencies ->
            result.addAll(transitiveClosure(dependencies))
        }
        result.addAll(runClasspathContributors(project, context))
        result.addAll(dependentProjectDependencies(dependentProjects, project, context))

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
    fun transitiveClosure(dependencies : List<IClasspathDependency>): List<IClasspathDependency> {
        var executor = executors.newExecutor("JvmCompiler}", 10)

        var result = hashSetOf<IClasspathDependency>()

        dependencies.forEach { projectDependency ->
            result.add(projectDependency)
            projectDependency.id.let {
                result.add(depFactory.create(it, executor = executor))
                val downloaded = transitiveClosure(projectDependency.directDependencies())

                result.addAll(downloaded)
            }
        }

        val result2 = reorderDependencies(result).filter {
            // Only keep existent files (nonexistent files are probably optional dependencies or parent poms
            // that point to other poms but don't have a jar file themselves)
            it.jarFile.get().exists()
        }

        executor.shutdown()

        return result2
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
    private fun dependentProjectDependencies(projectDescriptions: List<ProjectDescription>,
            project: Project?, context: KobaltContext) : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        projectDescriptions.filter {
            it.project.name == project?.name
        }.forEach { pd ->
            pd.dependsOn.forEach { p ->
                result.add(FileDependency(KFiles.joinDir(p.directory, p.classesDir(context))))
                val otherDependencies = calculateDependencies(p, context, projectDescriptions,
                        p.compileDependencies)
                result.addAll(otherDependencies)
            }
        }

        return result
    }

    private fun dependencies(project: Project, context: KobaltContext, isTest: Boolean)
            : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        val projects = listOf(ProjectDescription(project, project.projectExtra.dependsOn))
        with(project) {
            val deps = arrayListOf(compileDependencies, compileProvidedDependencies)
            if (isTest) {
                deps.add(testDependencies)
                deps.add(testProvidedDependencies)
            }
            deps.forEach {
                result.addAll(calculateDependencies(project, context, projects, it))
            }
        }

        // Make sure that classes/ and test-classes/ are always at the top of this classpath,
        // so that older versions of that project on the classpath don't shadow them
        val result2 = arrayListOf(FileDependency(KFiles.makeOutputDir(project).absolutePath),
            FileDependency(KFiles.makeOutputTestDir(project).absolutePath)) +
            reorderDependencies(result)
        return result2
    }

}
