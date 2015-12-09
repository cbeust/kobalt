package com.beust.kobalt.misc

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.maven.MavenId
import javax.inject.Inject

/**
 * Find out if any newer versions of the dependencies are available.
 */
public class CheckVersions @Inject constructor(val depFactory : DepFactory,
        val executors : KobaltExecutors) {

    fun run(projects: List<Project>) {
        val executor = executors.newExecutor("CheckVersions", 5)

        val newVersions = hashSetOf<String>()
        projects.forEach {
            arrayListOf(it.compileDependencies, it.testDependencies).forEach { cds ->
                cds.forEach { compileDependency ->
                    if (MavenId.isMavenId(compileDependency.id)) {
                        val dep: IClasspathDependency
                        try {
                            dep = depFactory.create(compileDependency.shortId, executor, false /* go remote */)
                        } catch(e: Exception) {
                            log(1, "cannot resolve ${compileDependency.shortId}. ignoring")
                            return@forEach
                        }
                        if (dep is MavenDependency) {
                            val other = compileDependency as MavenDependency
                            if (dep.id != compileDependency.id
                                    && Versions.toLongVersion(dep.version) > Versions.toLongVersion(other.version)) {
                                newVersions.add(dep.id)
                            }
                        }
                    }
                }
            }
        }

        if (newVersions.size > 0) {
            log(1, "New versions found:")
            newVersions.forEach { log(1, "        $it") }
        } else {
            log(1, "All dependencies up to date")
        }
        executor.shutdown()
    }
}
