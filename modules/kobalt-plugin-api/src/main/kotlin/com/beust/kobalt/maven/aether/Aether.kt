package com.beust.kobalt.maven.aether

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.homeDir
import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.Versions
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
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

val TEST_DIR = ".aether/repository"

class KobaltAether(val localRepo: File = File(homeDir(TEST_DIR))) {
    fun create(id: String): IClasspathDependency {
        val aether = Aether(localRepo)
        val cr = aether.transitiveDependencies(DefaultArtifact(id))
        return if (cr != null) AetherDependency(cr.root.artifact)
            else throw KobaltException("Couldn't resolve $id")
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

class Aether(val localRepo: File = File(homeDir(TEST_DIR))) {
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

    fun directDependencies(artifact: Artifact): CollectResult? {
        val result = system.collectDependencies(session, collectRequest(artifact))
        val root = result.root
        return result
    }
}

class AetherDependency(val artifact: Artifact): IClasspathDependency, Comparable<AetherDependency> {
    constructor(node: DependencyNode) : this(node.artifact) {}

    override val id: String = toId(artifact)

    private fun toId(a: Artifact) = with(a) {
        groupId + ":" + artifactId + ":" + version
    }

    override val jarFile: Future<File>
        get() = if (artifact.file != null) {
            CompletedFuture(artifact.file)
        } else {
            val td = Aether().transitiveDependencies(artifact)
            if (td?.root?.artifact?.file != null) {
                CompletedFuture(td!!.root.artifact.file)
            } else {
                val resolved = Aether().resolve(artifact)
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
        val deps = Aether().directDependencies(artifact)
        val td = Aether().transitiveDependencies(artifact)
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

fun main(argv: Array<String>) {
    KobaltLogger.LOG_LEVEL = 2
    val aether = Aether()
    val artifact = DefaultArtifact("org.testng:testng:(0,]")
    aether.resolveVersion(artifact)?.let { versionResult ->
        println("Latest version: " + versionResult + " " + versionResult.highestVersion)
        println("")
//        val newArtifact = DefaultArtifact(artifact.groupId, artifact.artifactId, artifact.extension,
//                versionResult.highestVersion)
//        val artifactResult = aether.resolve(newArtifact)
//        println("  File: " + artifactResult)
    }
    val d2 = Aether().resolve(artifact)
//    val dd = Aether().resolve("org.testng:testng:6.9.9")
//    val artifact = d2?.root?.artifact
    if (d2 != null && d2.size > 0) {
        println("DD: " + d2)
    }
}
