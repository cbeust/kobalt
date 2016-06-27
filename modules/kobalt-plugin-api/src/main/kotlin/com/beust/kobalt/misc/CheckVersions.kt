package com.beust.kobalt.misc

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.maven.aether.Aether
import com.beust.kobalt.maven.aether.AetherDependency
import javax.inject.Inject

/**
 * Find out if any newer versions of the dependencies are available.
 */
public class CheckVersions @Inject constructor(val depManager: DependencyManager,
        val executors : KobaltExecutors, val aether: Aether) {

    fun run(projects: List<Project>) {
        val executor = executors.newExecutor("CheckVersions", 5)

        val newVersions = hashSetOf<String>()
        projects.forEach { project ->
            listOf(project.compileDependencies, project.testDependencies).forEach { cds ->
                cds.forEach { dep ->
                    if (MavenId.isMavenId(dep.id)) {
                        try {
                            val latestDep = depManager.create(dep.shortId, project.directory)
                            val artifact = (latestDep as AetherDependency).artifact
                            val versions = aether.resolveVersion(artifact)
                            val highest = versions?.highestVersion?.toString()
                            if (highest != null && highest != dep.id
                                   && Versions.toLongVersion(highest) > Versions.toLongVersion(dep.version)) {
                                newVersions.add(artifact.groupId + ":" + artifact.artifactId + ":" + highest)
                            }
                        } catch(e: KobaltException) {
                            log(1, "  Cannot resolve ${dep.shortId}. ignoring")
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
