package com.beust.kobalt.maven

import com.beust.kobalt.api.*
import com.google.common.collect.ArrayListMultimap
import java.util.*
import com.beust.kobalt.misc.*
import javax.inject.*

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
    // TODO it may cause problems if there are file dependencies in the list, we can't compare file dependencies with maven dependencies
    public fun reorderDependencies(dependencies: Collection<IClasspathDependency>): List<IClasspathDependency> {
        return dependencies.groupBy { it.shortId + ":" + it.classifierIfAvailable() }
                .map { it.value.filterIsInstance<MavenDependency>().max() as? IClasspathDependency
                        ?: it.value.filterIsInstance<FileDependency>().max()
                        ?: it.value.first() }
    }

    private fun IClasspathDependency.classifierIfAvailable() = if (this is MavenDependency) mavenId.classifier else ""
}
