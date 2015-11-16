package com.beust.kobalt.maven

import com.beust.kobalt.misc.*
import java.util.concurrent.*
import javax.inject.*

public class DepFactory @Inject constructor(val localRepo: LocalRepo,
        val repoFinder: RepoFinder,
        val executors: KobaltExecutors,
        val downloadManager: DownloadManager,
        val pomFactory: Pom.IFactory) {

    /**
     * Parse the id and return the correct IClasspathDependency
     */
    public fun create(id: String, executor: ExecutorService,
            localFirst : Boolean = true) : IClasspathDependency {
        if (id.startsWith(IClasspathDependency.PREFIX_FILE)) {
            return FileDependency(id.substring(IClasspathDependency.PREFIX_FILE.length))
        } else {
            val mavenId = MavenId(id)

            val resolvedVersion: String = when {
                mavenId.version != null -> mavenId.version
                else -> resolve(mavenId, localFirst)
            }

            return MavenDependency(MavenId(mavenId.groupId, mavenId.artifactId, resolvedVersion, mavenId.packaging, mavenId.classifier),
                    executor, localRepo, repoFinder, pomFactory, downloadManager)
        }
    }

    private fun resolve(mavenId: MavenId, localFirst: Boolean) =
            (if (localFirst) localRepo.findLocalVersion(mavenId) else null)
                ?: repoFinder.findCorrectRepo(mavenId.id).let { if (it.found) it.version else null }
                ?: throw KobaltException("Failed to resolve ${mavenId.id}")
}
