package com.beust.kobalt.api

import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.KobaltMavenResolver
import com.beust.kobalt.misc.kobaltLog
import java.io.File
import java.util.ArrayList
import java.util.concurrent.Future

class Dependencies(val project: Project,
                   val dependencies: ArrayList<IClasspathDependency>,
                   val optionalDependencies: ArrayList<IClasspathDependency>,
                   val providedDependencies: ArrayList<IClasspathDependency>,
                   val runtimeDependencies: ArrayList<IClasspathDependency>,
                   val excludedDependencies: ArrayList<IClasspathDependency>,
                   val nativeDependencies: ArrayList<IClasspathDependency>) {

    /**
     * Add the dependencies to the given ArrayList and return a list of future jar files corresponding to
     * these dependencies. Futures are necessary here since this code is invoked from the build file and
     * we might not have set up the extra IRepositoryContributors just yet. By the time these
     * future tasks receive a get(), the repos will be correct.
     */
    private fun addToDependencies(project: Project, dependencies: ArrayList<IClasspathDependency>,
                                  dep: Array<out String>, optional: Boolean = false): List<Future<File>>
        = with(dep.map {
            val resolved =
                if (KobaltMavenResolver.isRangeVersion(it)) {
                    // Range id
                    val node = Kobalt.INJECTOR.getInstance(KobaltMavenResolver::class.java).resolveToArtifact(it)
                    val result = KobaltMavenResolver.artifactToId(node)
                    kobaltLog(2, "Resolved range id $it to $result")
                    result
                } else {
                    it
                }
            DependencyManager.create(resolved, optional, project.directory)
        }) {
            dependencies.addAll(this)
            this.map { java.util.concurrent.FutureTask { it.jarFile.get() } }
        }

    @Directive
    fun compile(vararg dep: String) = addToDependencies(project, dependencies, dep)

    @Directive
    fun compileOptional(vararg dep: String) {
        addToDependencies(project, optionalDependencies, dep, optional = true)
        addToDependencies(project, dependencies, dep, optional = true)
    }

    @Directive
    fun provided(vararg dep: String) {
        addToDependencies(project, providedDependencies, dep)
        addToDependencies(project, dependencies, dep)
    }

    @Directive
    fun runtime(vararg dep: String) = addToDependencies(project, runtimeDependencies, dep)

    @Directive
    fun exclude(vararg dep: String) = addToDependencies(project, excludedDependencies, dep)

    @Directive
    fun native(vararg dep: String) = addToDependencies(project, nativeDependencies, dep)
}