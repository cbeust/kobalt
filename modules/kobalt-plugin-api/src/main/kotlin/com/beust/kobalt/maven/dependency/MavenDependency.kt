package com.beust.kobalt.maven.dependency

import com.beust.kobalt.HostConfig
import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.DependencyExecutor
import com.beust.kobalt.misc.Versions
import com.beust.kobalt.misc.warn
import com.google.inject.Key
import com.google.inject.assistedinject.Assisted
import org.apache.maven.model.Dependency
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import javax.inject.Inject
import kotlin.properties.Delegates

class MavenDependency @Inject constructor(
        @Assisted mavenId: MavenId,
        @Assisted val executor: ExecutorService,
        @Assisted("downloadSources") val downloadSources: Boolean,
        @Assisted("downloadJavadocs") val downloadJavadocs: Boolean,
        override val localRepo: LocalRepo,
        val repoFinder: RepoFinder,
        val pomFactory: Pom.IFactory,
        val downloadManager: DownloadManager)
            : LocalDep(mavenId, localRepo), IClasspathDependency, Comparable<MavenDependency> {
    override var jarFile: Future<File> by Delegates.notNull()
    var pomFile: Future<File> by Delegates.notNull()

    interface IFactory {
        fun create(mavenId: MavenId, executor: ExecutorService,
                @Assisted("downloadSources") downloadSources: Boolean,
                @Assisted("downloadJavadocs") downloadJavadocs: Boolean) : MavenDependency
    }

    init {
        val jar = File(localRepo.toFullPath(toJarFile(version)))
        val aar = File(localRepo.toFullPath(toAarFile(version)))
        val pom = File(localRepo.toFullPath(toPomFile(version)))

        fun toSuffix(name: String, suffix: String = "") : String {
            val dot = name.lastIndexOf(".")
            return name.substring(0, dot) + suffix + name.substring(dot)
        }

        fun download(url: String, fileName: String, suffix: String = "") : Future<File> {
            val localPath = localRepo.toFullPath(toSuffix(fileName, suffix))
            return downloadManager.download(HostConfig(toSuffix(url, suffix)), localPath, executor)
        }

        if (pom.exists() && (jar.exists() || aar.exists())) {
            jarFile = CompletedFuture(if (jar.exists()) jar else aar)
            pomFile = CompletedFuture(pom)
        } else {
            val repoResult = repoFinder.findCorrectRepo(mavenId.toId)

            if (repoResult.found) {
                jarFile =
                    if (repoResult.archiveUrl != null) {
                        download(repoResult.archiveUrl, repoResult.path!!)
                    } else {
                        CompletedFuture(File("nonexistentFile")) // will be filtered out
                    }
                pomFile = downloadManager.download(HostConfig(url = repoResult.hostConfig.url + toPomFile(repoResult)),
                        pom.absolutePath, executor)
            } else {
                throw KobaltException("Couldn't resolve ${mavenId.toId}")
            }
        }

        if (downloadSources || downloadJavadocs) {
            val repoResult = repoFinder.findCorrectRepo(mavenId.toId)
            if (repoResult.archiveUrl != null && repoResult.path != null) {
                if (downloadSources) {
                    download(repoResult.archiveUrl, repoResult.path, "-sources")
                }
                if (downloadJavadocs) {
                    download(repoResult.archiveUrl, repoResult.path, "-javadoc")
                }
            }
        }

    }

    companion object {
        val defaultExecutor =
                Kobalt.INJECTOR.getInstance(Key.get(ExecutorService::class.java, DependencyExecutor::class.java))
        val depFactory = Kobalt.INJECTOR.getInstance(DepFactory::class.java)

        fun create(id: String, downloadSources: Boolean = false, downloadJavadocs: Boolean = false,
                executor: ExecutorService = defaultExecutor)
                    = depFactory.create(id, downloadSources, downloadJavadocs, executor = executor)

        fun create(mavenId: MavenId, downloadSources: Boolean = false, downloadJavadocs: Boolean = false,
                executor: ExecutorService = defaultExecutor)
                    = create(mavenId.toId, downloadSources, downloadJavadocs, executor)
    }

    override fun toString() = mavenId.toId

    override val id = mavenId.toId

    override fun toMavenDependencies() = let { md ->
        Dependency().apply {
            groupId = md.groupId
            artifactId = md.artifactId
            version = md.version
        }
    }

    override fun compareTo(other: MavenDependency): Int {
        return Versions.toLongVersion(version).compareTo(Versions.toLongVersion(other.version))
    }

    override val shortId = "$groupId:$artifactId:"

    override fun directDependencies() : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        try {
            val pom = pomFactory.create(id, pomFile.get())
            pom.dependencies.filter {
                it.mustDownload
            }.forEach {
                result.add(create(MavenId.toId(it.groupId, it.artifactId, it.packaging, it.version)))
            }
        } catch(ex: Exception) {
            warn("Exception when trying to resolve dependencies for $id: " + ex.message)
        }
        return result
    }
}

