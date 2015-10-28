package com.beust.kobalt.maven

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.*
import com.google.inject.Key
import com.google.inject.assistedinject.Assisted
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import javax.inject.Inject
import kotlin.properties.Delegates

public class MavenDependency @Inject constructor(override @Assisted("groupId") val groupId : String,
        override @Assisted("artifactId") val artifactId : String,
        override @Assisted("packaging") val packaging: String?,
        override @Assisted("version") val version : String,
        val executor: ExecutorService,
        override val localRepo: LocalRepo,
        val repoFinder: RepoFinder,
        val pomFactory: Pom.IFactory,
        val downloadManager: DownloadManager)
            : LocalDep(groupId, artifactId, packaging, version, localRepo), IClasspathDependency,
                Comparable<MavenDependency> {
    override var jarFile: Future<File> by Delegates.notNull()
    var pomFile: Future<File> by Delegates.notNull()

    init {
        val jar = File(localRepo.toFullPath(toJarFile(version)))
        val pom = File(localRepo.toFullPath(toPomFile(version)))
        if (jar.exists() && pom.exists()) {
            jarFile = CompletedFuture(jar)
            pomFile = CompletedFuture(pom)
        } else {
            val repoResult = repoFinder.findCorrectRepo(toId(groupId, artifactId, packaging, version))
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
                throw KobaltException("Couldn't resolve ${toId(groupId, artifactId, packaging, version)}")
            }
        }
    }

//    interface IFactory {
//        fun _create(@Assisted("groupId") groupId: String,
//                @Assisted("artifactId") artifactId: String,
//                @Assisted("version") version: String = "",
//                executor: ExecutorService) : MavenDependency
//    }

    companion object {
        val executor = Kobalt.INJECTOR.getInstance(Key.get(ExecutorService::class.java, DependencyExecutor::class.java))
        val depFactory = Kobalt.INJECTOR.getInstance(DepFactory::class.java)

        fun create(id: String, ex: ExecutorService = executor) : IClasspathDependency {
            return depFactory.create(id, ex)
        }

        fun toId(g: String, a: String, packaging: String?, v: String) =
                if (packaging.isNullOrBlank()) "$g:$a:$v"
                else "$g:$a:$packaging:$v"
    }


    public override fun toString() = toId(groupId, artifactId, packaging, version)

    override val id = toId(groupId, artifactId, packaging, version)

    override fun toMavenDependencies(): org.apache.maven.model.Dependency {
        with(org.apache.maven.model.Dependency()) {
            setGroupId(groupId)
            setArtifactId(artifactId)
            setVersion(version)
            return this
        }
    }

    override fun compareTo(other: MavenDependency): Int {
        return Versions.toLongVersion(version).compareTo(Versions.toLongVersion(other.version))
    }

    override val shortId = groupId + ":" + artifactId

    override fun directDependencies() : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        pomFactory.create(id, pomFile.get()).dependencies.filter {
            it.mustDownload && it.isValid
        }.forEach {
            result.add(create(toId(it.groupId, it.artifactId, it.packaging, it.version)))
        }
        return result
    }
}

