package com.beust.kobalt.maven

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.DependencyExecutor
import com.beust.kobalt.misc.KobaltExecutors
import com.google.inject.Key
import java.util.concurrent.ExecutorService
import javax.inject.Inject

public class DepFactory @Inject constructor(val localRepo: LocalRepo,
        val repoFinder: RepoFinder,
        val executors: KobaltExecutors,
        val downloadManager: DownloadManager,
        val pomFactory: Pom.IFactory) {

    companion object {
        val defExecutor : ExecutorService by lazy {
            Kobalt.INJECTOR.getInstance(Key.get(ExecutorService::class.java, DependencyExecutor::class.java))
        }
    }

    /**
     * Parse the id and return the correct IClasspathDependency
     */
    public fun create(id: String, executor: ExecutorService = defExecutor, localFirst : Boolean = true)
            : IClasspathDependency {
        if (id.startsWith(FileDependency.PREFIX_FILE)) {
            return FileDependency(id.substring(FileDependency.PREFIX_FILE.length))
        } else {
            val mavenId = MavenId.create(id)
            var packaging = mavenId.packaging
            var repoResult: RepoFinder.RepoResult?

            val version = mavenId.version ?:
                if (localFirst) {
                    localRepo.findLocalVersion(mavenId.groupId, mavenId.artifactId, mavenId.packaging)
                } else {
                    repoResult = repoFinder.findCorrectRepo(id)
                    if (!repoResult.found) {
                        throw KobaltException("Couldn't resolve $id")
                    } else {
                        repoResult.version?.version
                    }
                }

            return MavenDependency(MavenId.create(mavenId.groupId, mavenId.artifactId, packaging, version),
                    executor, localRepo, repoFinder, pomFactory, downloadManager)
        }
    }
}
