package com.beust.kobalt.misc

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.maven.aether.AetherDependency
import javax.inject.Inject

/**
 * Find out if any newer versions of the dependencies are available.
 */
public class CheckVersions @Inject constructor(val depManager: DependencyManager,
        val executors : KobaltExecutors) {

    fun run(projects: List<Project>) {
        val executor = executors.newExecutor("CheckVersions", 5)

        val newVersions = hashSetOf<String>()
        projects.forEach { project ->
            listOf(project.compileDependencies, project.testDependencies).forEach { cds ->
                cds.forEach { compileDependency ->
                    if (MavenId.isMavenId(compileDependency.id)) {
                        try {
                            val dep = depManager.create(compileDependency.shortId, project.directory)
                            val other = compileDependency as AetherDependency
                            if (dep.id != compileDependency.id
                                   && Versions.toLongVersion(dep.version) > Versions.toLongVersion(other.version)) {
                                newVersions.add(dep.id)
                            }
                        } catch(e: KobaltException) {
                            log(1, "  Cannot resolve ${compileDependency.shortId}. ignoring")
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
