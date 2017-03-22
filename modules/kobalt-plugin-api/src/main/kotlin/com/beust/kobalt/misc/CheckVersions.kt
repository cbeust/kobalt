package com.beust.kobalt.misc

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.maven.aether.AetherDependency
import com.beust.kobalt.maven.aether.KobaltMavenResolver
import javax.inject.Inject

/**
 * Find out if any newer versions of the dependencies are available.
 */
class CheckVersions @Inject constructor(val depManager: DependencyManager,
        val executors : KobaltExecutors, val resolver: KobaltMavenResolver) {

    fun run(projects: List<Project>) = projects.forEach { run(it) }

    fun run(project: Project) {
        val executor = executors.newExecutor("CheckVersions", 5)

        val newVersions = hashSetOf<String>()
        listOf(project.compileDependencies, project.testDependencies).forEach { cds ->
            cds.forEach { dep ->
                if (MavenId.isMavenId(dep.id)) {
                    try {
                        val latestDep = depManager.create(dep.shortId, false, project.directory)
                        val artifact = (latestDep as AetherDependency).artifact
                        val versions = resolver.resolveVersion(artifact)
                        val releases = versions?.versions?.filter { !it.toString().contains("SNAP")}
                        val highest = if (releases != null && releases.any()) {
                                releases.last().toString()
                            } else {
                                versions?.highestVersion.toString()
                            }
                        if (highest != dep.id
                                && StringVersion(highest) > StringVersion(dep.version)) {
                            newVersions.add(artifact.groupId + ":" + artifact.artifactId + ":" + highest)
                        }
                    } catch(e: KobaltException) {
                        kobaltLog(1, "  Cannot resolve ${dep.shortId}. ignoring")
                    }
                }
            }
        }

        if (newVersions.size > 0) {
            kobaltLog(1, "  New versions found:")
            newVersions.forEach { kobaltLog(1, "    $it") }
        } else {
            kobaltLog(1, "  All dependencies up to date")
        }
        executor.shutdown()
    }
}
