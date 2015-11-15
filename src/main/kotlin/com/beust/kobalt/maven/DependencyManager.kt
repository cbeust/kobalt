package com.beust.kobalt.maven

import com.beust.kobalt.api.IClasspathContributor
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KobaltExecutors
import com.google.common.collect.ArrayListMultimap
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class DependencyManager @Inject constructor(val executors: KobaltExecutors,
        val depFactory: DepFactory){

    /**
     * @return the classpath for this project, including the IClasspathContributors.
     */
    fun calculateDependencies(project: Project?, context: KobaltContext,
            vararg allDependencies: List<IClasspathDependency>): List<IClasspathDependency> {
        var result = arrayListOf<IClasspathDependency>()
        allDependencies.forEach { dependencies ->
            result.addAll(transitiveClosure(dependencies))
        }
        result.addAll(runClasspathContributors(project, context))

        return result
    }

    private fun runClasspathContributors(project: Project?, context: KobaltContext) :
            Collection<IClasspathDependency> {
        val result = hashSetOf<IClasspathDependency>()
        context.pluginInfo.classpathContributors.forEach { it: IClasspathContributor ->
            result.addAll(it.entriesFor(project))
        }
        return result
    }

    fun transitiveClosure(dependencies : List<IClasspathDependency>): List<IClasspathDependency> {
        var executor = executors.newExecutor("JvmCompiler}", 10)

        var result = hashSetOf<IClasspathDependency>()

        dependencies.forEach { projectDependency ->
            result.add(projectDependency)
            projectDependency.id.let {
                result.add(depFactory.create(it, executor))
                val downloaded = projectDependency.transitiveDependencies(executor)

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
    public fun reorderDependencies(dependencies: Collection<IClasspathDependency>): List<IClasspathDependency> {
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
            result.add(l.get(0))
        }
        return result
    }
}
