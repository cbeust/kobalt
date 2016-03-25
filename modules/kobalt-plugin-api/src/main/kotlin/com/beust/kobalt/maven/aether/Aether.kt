package com.beust.kobalt.maven.aether

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.misc.Versions
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.inject.Inject
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.CollectResult
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyFilter
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.metadata.DefaultMetadata
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.*
import org.eclipse.aether.util.artifact.JavaScopes
import org.eclipse.aether.util.filter.AndDependencyFilter
import org.eclipse.aether.util.filter.DependencyFilterUtils
import java.io.File
import java.util.concurrent.Future

class DependencyResult(val dependency: IClasspathDependency, val repoUrl: String)

class KobaltAether @Inject constructor (val settings: KobaltSettings) {
    val localRepo: File get() = File(settings.localRepo)

    companion object {
        val aether : KobaltAether get() = Kobalt.INJECTOR.getInstance(KobaltAether::class.java)

        fun create(id: String) = aether.create(id)
    }

    /**
     * Don't call this method directly, use `DepFactory` instead.
     */
    fun create(id: String): IClasspathDependency {
        val aether = Aether(localRepo)
        val cr = aether.transitiveDependencies(DefaultArtifact(id))
        return if (cr != null) AetherDependency(cr.root.artifact)
            else throw KobaltException("Couldn't resolve $id")
    }

    fun latestArtifact(group: String, artifactId: String, extension: String = "jar") : DependencyResult
        = Aether(localRepo).latestArtifact(group, artifactId, extension).let {
            DependencyResult(AetherDependency(it.artifact), it.repository.toString())
        }

    fun resolve(id: String): DependencyResult {
        val results = Aether(localRepo).resolve(DefaultArtifact(id))
        if (results != null && results.size > 0) {
            return DependencyResult(AetherDependency(results[0].artifact), results[0].repository.toString())
        } else {
            throw KobaltException("Couldn't resolve $id")
        }
    }
}

class ExcludeOptionalDependencyFilter: DependencyFilter {
    override fun accept(node: DependencyNode?, p1: MutableList<DependencyNode>?): Boolean {
//        val result = node != null && ! node.dependency.isOptional
        val accept1 = node == null || node.artifact.artifactId != "srczip"
        val accept2 = node != null && ! node.dependency.isOptional
        val result = accept1 && accept2
        return result
    }
}

class Aether(val localRepo: File) {
    private val system = Booter.newRepositorySystem()
    private val session = Booter.newRepositorySystemSession(system, localRepo)
    private val classpathFilter = AndDependencyFilter(
            ExcludeOptionalDependencyFilter(),
            DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE))
    private val kobaltRepositories : List<RemoteRepository>
            get() = Kobalt.repos.map {
                RemoteRepository.Builder("maven", "default", it.url)
//                    .setSnapshotPolicy(RepositoryPolicy(false, null, null))
                    .build()
            }

    private fun collectRequest(artifact: Artifact) : CollectRequest {
        with(CollectRequest()) {
            root = Dependency(artifact, JavaScopes.COMPILE)
            repositories = kobaltRepositories

            return this
        }
    }

    fun latestArtifact(group: String, artifactId: String, extension: String = "jar") : ArtifactResult {
        val artifact = DefaultArtifact(group, artifactId, extension, "(0,]")
        val resolved = resolveVersion(artifact)
        if (resolved != null) {
            val newArtifact = DefaultArtifact(artifact.groupId, artifact.artifactId, artifact.extension,
                    resolved.highestVersion.toString())
            val artifactResult = resolve(newArtifact)
            if (artifactResult != null) {
                    return artifactResult[0]
            } else {
                throw KobaltException("Couldn't find latest artifact for $group:$artifactId")
            }
        } else {
            throw KobaltException("Couldn't find latest artifact for $group:$artifactId")
        }
    }

    fun resolveVersion(artifact: Artifact): VersionRangeResult? {
        val metadata = DefaultMetadata(artifact.groupId, artifact.artifactId, "maven-metadata.xml",
                org.eclipse.aether.metadata.Metadata.Nature.RELEASE)

        val r = system.resolveMetadata(session, kobaltRepositories.map {
            MetadataRequest(metadata, it, null).apply {
                isFavorLocalRepository = false
            }
        })


//        kobaltRepositories.forEach {
//            val request = MetadataRequest(metadata, it, null).apply {
//                isFavorLocalRepository = false
//            }
//            val r = system.resolveMetadata(session, listOf(request))
//            println("Repo: $it " + r)
//        }
        val request = VersionRangeRequest(artifact, kobaltRepositories, null)
        val result = system.resolveVersionRange(session, request)
        return result
    }

    fun resolve(artifact: Artifact): List<ArtifactResult>? {
        try {
            val dependencyRequest = DependencyRequest(collectRequest(artifact), classpathFilter)

            val result = system.resolveDependencies(session, dependencyRequest).artifactResults
            return result
        } catch(ex: DependencyResolutionException) {
            warn("Couldn't resolve $artifact")
            return emptyList()
        }
    }

    fun transitiveDependencies(artifact: Artifact) = directDependencies(artifact)

    fun directDependencies(artifact: Artifact): CollectResult?
            = system.collectDependencies(session, collectRequest(artifact))
}

class AetherDependency(val artifact: Artifact): IClasspathDependency, Comparable<AetherDependency> {
    val settings : KobaltSettings get() = Kobalt.INJECTOR.getInstance(KobaltSettings::class.java)
    val localRepo : File get() = File(settings.localRepo)
    val aether: Aether get() = Aether(localRepo)

    constructor(node: DependencyNode) : this(node.artifact) {}

    override val id: String = toId(artifact)

    override val version: String = artifact.version

    private fun toId(a: Artifact) = with(a) {
        groupId + ":" + artifactId + ":" + version
    }

    override val jarFile: Future<File>
        get() = if (artifact.file != null) {
            CompletedFuture(artifact.file)
        } else {
            val td = aether.transitiveDependencies(artifact)
            if (td?.root?.artifact?.file != null) {
                CompletedFuture(td!!.root.artifact.file)
            } else {
                val resolved = Aether(localRepo).resolve(artifact)
                if (resolved != null && resolved.size > 0) {
                    CompletedFuture(resolved[0].artifact.file)
                } else {
                    CompletedFuture(File("DONOTEXIST")) // will be filtered out
                }
            }
        }

    override fun toMavenDependencies() = let { md ->
        org.apache.maven.model.Dependency().apply {
            artifact.let { md ->
                groupId = md.groupId
                artifactId = md.artifactId
                version = md.version
            }
        }
    }

    override fun directDependencies() : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        val deps = aether.directDependencies(artifact)
        val td = aether.transitiveDependencies(artifact)
        if (deps != null) {
            if (! deps.root.dependency.optional) {
                deps.root.children.forEach {
                    if (! it.dependency.isOptional) {
                        result.add(AetherDependency(it.artifact))
                    } else {
                        log(2, "Skipping optional dependency " + deps.root.artifact)
                    }
                }
            } else {
                log(2, "Skipping optional dependency " + deps.root.artifact)
            }
        } else {
            warn("Couldn't resolve $artifact")
        }
        return result
    }

    override val shortId = artifact.groupId + ":" + artifact.artifactId

    override fun compareTo(other: AetherDependency): Int {
        return Versions.toLongVersion(artifact.version).compareTo(Versions.toLongVersion(
                other.artifact.version))
    }

    override fun toString() = id
}

//fun main(argv: Array<String>) {
//    KobaltLogger.LOG_LEVEL = 2
//    val aether = Aether()
//    val latestResult = aether.latestArtifact("org.testng", "testng")
//    val latest = latestResult.artifact
//    println("Latest: " + latest.version + " " + latest.file)
//}
