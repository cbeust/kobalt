package com.beust.kobalt.maven

import com.beust.kobalt.misc.KobaltExecutors
import java.util.concurrent.ExecutorService
import javax.inject.Inject

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
            return FileDependency(id.substring(IClasspathDependency.PREFIX_FILE.length()))
        } else {
            val c = id.split(":")
            var repoResult: RepoFinder.RepoResult?
            var version: String? = null

            if (! MavenDependency.hasVersion(id)) {
                if (localFirst) version = localRepo.findLocalVersion(c[0], c[1])
                if (! localFirst || version == null) {
                    repoResult = repoFinder.findCorrectRepo(id)
                    if (!repoResult.found) {
                        throw KobaltException("Couldn't resolve ${id}")
                    } else {
                        version = repoResult.version
                    }
                }
            } else {
                version = c[2]
            }

            return MavenDependency(c[0], c[1], version, executor, localRepo, repoFinder,
                    pomFactory, downloadManager)
        }
    }
}
