package com.beust.kobalt.maven

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.DependencyExecutor
import com.beust.kobalt.misc.Versions
import com.beust.kobalt.misc.warn
import com.google.inject.Key
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import com.beust.kobalt.api.*
import com.beust.kobalt.misc.*
import com.google.inject.*
import java.io.*
import java.util.concurrent.*
import javax.inject.Inject
import kotlin.properties.*

public class MavenDependency @Inject constructor(mavenId: MavenId,
        val executor: ExecutorService,
        override val localRepo: LocalRepo,
        val repoFinder: RepoFinder,
        val pomFactory: Pom.IFactory,
        val downloadManager: DownloadManager)
            : LocalDep(mavenId, localRepo), IClasspathDependency, Comparable<MavenDependency> {
    override var jarFile: Future<File> by Delegates.notNull()
    var pomFile: Future<File> by Delegates.notNull()

    init {
        val jar = File(localRepo.toFullPath(toJarFile(version)))
        val pom = File(localRepo.toFullPath(toPomFile(version)))
        if (jar.exists() && pom.exists()) {
            jarFile = CompletedFuture(jar)
            pomFile = CompletedFuture(pom)
        } else {
            val repoResult = repoFinder.findCorrectRepo(mavenId.id)
            if (repoResult.found) {
                jarFile =
                    if (repoResult.hasJar) {
                        downloadManager.download(repoResult.repoUrl + toJarFile(repoResult), jar.absolutePath, executor)
                    } else {
                        CompletedFuture(File("nonexistentFile")) // will be filtered out
                }
                pomFile = downloadManager.download(repoResult.repoUrl + toPomFile(repoResult), pom.absolutePath,
                        executor)
            } else {
                throw KobaltException("Couldn't resolve ${mavenId.id}")
            }
        }
    }

    companion object {
        val executor = Kobalt.INJECTOR.getInstance(Key.get(ExecutorService::class.java, DependencyExecutor::class.java))
        val depFactory = Kobalt.INJECTOR.getInstance(DepFactory::class.java)

        fun create(id: String, ex: ExecutorService = executor) : IClasspathDependency {
            return depFactory.create(id, ex)
        }
    }


    public override fun toString() = mavenId.id

    override val id = mavenId.id

    override fun toMavenDependencies(): org.apache.maven.model.Dependency {
        return org.apache.maven.model.Dependency().apply {
            setGroupId(groupId)
            setArtifactId(artifactId)
            setVersion(version)
            setClassifier(mavenId.classifier)
        }
    }

    override fun compareTo(other: MavenDependency): Int {
        return Versions.toLongVersion(version).compareTo(Versions.toLongVersion(other.version))
    }

    override val shortId = "$groupId:$artifactId:"

    override fun directDependencies() : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        try {
            pomFactory.create(id, pomFile.get()).dependencies.filter {
                it.mustDownload && it.isValid
            }.forEach {
                result.add(create(MavenId.toId(it.groupId, it.artifactId, it.packaging, it.version, it.classifier)))
            }
        } catch(ex: Exception) {
            warn("Exception when trying to resolve dependencies for $id: " + ex.message)
        }
        return result
    }
}

