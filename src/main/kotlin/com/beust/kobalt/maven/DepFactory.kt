package com.beust.kobalt.maven

import com.beust.kobalt.KobaltException
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.maven.dependency.IClasspathDependency
import com.beust.kobalt.maven.dependency.MavenDependency
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
            return FileDependency(id.substring(IClasspathDependency.PREFIX_FILE.length))
        } else {
            val mavenId = MavenId(id)
            var version = mavenId.version
            var packaging = mavenId.packaging
            var repoResult: RepoFinder.RepoResult?

            if (! mavenId.hasVersion) {
                if (localFirst) version = localRepo.findLocalVersion(mavenId.groupId, mavenId.artifactId,
                        mavenId.packaging)
                if (! localFirst || version == null) {
                    repoResult = repoFinder.findCorrectRepo(id)
                    if (!repoResult.found) {
                        throw KobaltException("Couldn't resolve $id")
                    } else {
                        version = repoResult.version
                    }
                }
            }

            return MavenDependency(MavenId.create(mavenId.groupId, mavenId.artifactId, packaging, version!!),
                    executor, localRepo, repoFinder, pomFactory, downloadManager)
        }
    }
}
