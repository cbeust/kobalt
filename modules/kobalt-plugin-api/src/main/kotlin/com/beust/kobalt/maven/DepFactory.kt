package com.beust.kobalt.maven

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.aether.Aether
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.DependencyExecutor
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.warn
import com.google.inject.Key
import java.util.concurrent.ExecutorService
import javax.inject.Inject

public class DepFactory @Inject constructor(val localRepo: LocalRepo,
        val remoteRepo: RepoFinder,
        val executors: KobaltExecutors,
        val aether: Aether,
        val mavenDependencyFactory: MavenDependency.IFactory) {

    companion object {
        val defExecutor : ExecutorService by lazy {
            Kobalt.INJECTOR.getInstance(Key.get(ExecutorService::class.java, DependencyExecutor::class.java))
        }
    }

    /**
     * Parse the id and return the correct IClasspathDependency
     */
    fun create(id: String, downloadSources: Boolean = false, downloadJavadocs: Boolean = false,
            localFirst : Boolean = true, showNetworkWarning: Boolean = true, executor: ExecutorService = defExecutor)
            : IClasspathDependency {
        if (id.startsWith(FileDependency.PREFIX_FILE)) {
            return FileDependency(id.substring(FileDependency.PREFIX_FILE.length))
        } else {
            val mavenId = MavenId.create(id)
            var tentativeVersion = mavenId.version
            var packaging = mavenId.packaging
            var repoResult: RepoFinder.RepoResult?

            val version =
                if (tentativeVersion != null && ! MavenId.isRangedVersion(tentativeVersion)) tentativeVersion
                else {
                    var localVersion: String? = tentativeVersion
                    if (localFirst) localVersion = localRepo.findLocalVersion(mavenId.groupId, mavenId.artifactId,
                            mavenId.packaging)
                    if (localFirst && localVersion != null) {
                        localVersion
                    } else {
                        if (! localFirst && showNetworkWarning) {
                            warn("The id \"$id\" doesn't contain a version, which will cause a network call")
                        }
                        repoResult = remoteRepo.findCorrectRepo(id)
                        if (!repoResult.found) {
                            throw KobaltException("Couldn't resolve $id")
                        } else {
                            repoResult.version?.version
                        }
                    }
                }


            val resultMavenId = MavenId.create(mavenId.groupId, mavenId.artifactId, packaging, version)
            return mavenDependencyFactory.create(resultMavenId, executor, downloadSources, downloadJavadocs)
        }
    }
}
