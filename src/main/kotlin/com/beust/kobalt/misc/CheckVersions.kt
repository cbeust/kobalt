package com.beust.kobalt.misc

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.MavenDependency
import com.google.inject.Inject

/**
 * Find out if any newer versions of the dependencies are available.
 */
public class CheckVersions @Inject constructor(val depFactory : DepFactory,
        val executors : KobaltExecutors) : KobaltLogger {

    fun run(projects: List<Project>) {
        val executor = executors.newExecutor("CheckVersions", 5)

        val newVersions = hashSetOf<String>()
        projects.forEach {
            val kobaltDependency = arrayListOf(
                    MavenDependency.create("com.beust:kobalt:" + Kobalt.version, executor))
            arrayListOf(kobaltDependency, it.compileDependencies, it.testDependencies).forEach { cd ->
                cd.forEach {
                    val dep = depFactory.create(it.shortId, executor, false /* go remote */)
                    if (dep is MavenDependency && it is MavenDependency) {
                        if (dep.id != it.id
                                && Versions.toLongVersion(dep.version)
                                        > Versions.toLongVersion(it.version)) {
                            newVersions.add(dep.id)
                        }
                    }
                }
            }
        }

        if (newVersions.size() > 0) {
            log(1, "New versions found:")
            newVersions.forEach { log(1, "        ${it}") }
        } else {
            log(1, "All dependencies up to date")
        }
        executor.shutdown()
    }
}
