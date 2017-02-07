package com.beust.kobalt.maven.aether

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.getProxy
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.MavenId
import com.google.common.eventbus.EventBus
import com.google.inject.Inject
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.CollectResult
import org.eclipse.aether.graph.DefaultDependencyNode
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyFilter
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult

class KobaltMavenResolver @Inject constructor(val settings: KobaltSettings,
        localRepo: LocalRepo, eventBus: EventBus) {

    companion object {
        fun artifactToId(artifact: Artifact) = artifact.let {
            MavenId.toId(it.groupId, it.artifactId, it.extension, it.classifier, it.version)
        }
        fun isRangeVersion(id: String) = id.contains(",")
    }

    fun resolve(id: String, scope: Scope? = null, filter: DependencyFilter? = null): DependencyNode {
        val dependencyRequest = DependencyRequest(createCollectRequest(id, scope), filter)
        val result = system.resolveDependencies(session, dependencyRequest)

//        GraphUtil.displayGraph(listOf(result.root), { it -> it.children },
//                { it: DependencyNode, indent: String -> println(indent + it.toString()) })
        return result.root
    }

    fun resolve(artifact: Artifact, scope: Scope? = null, filter: DependencyFilter? = null)
        = resolve(artifactToId(artifact), scope, filter)

    fun resolveToIds(id: String, scope: Scope? = null, filter: DependencyFilter? = null) : List<String> {
        val root = resolve(id, scope, filter)
        val children =
            root.children.filter {
                filter == null || filter.accept(DefaultDependencyNode(it.dependency), emptyList())
            }.filter {
                it.dependency.scope != Scope.SYSTEM.scope
            }
        val result = listOf(artifactToId(root.artifact)) + children.flatMap {
                val thisId = artifactToId(it.artifact)
                resolveToIds(thisId, scope, filter)
            }
        return result
    }

    fun resolveToDependencies(id: String, scope: Scope? = null, filter: DependencyFilter? = null)
            : List<IClasspathDependency> {
        val result = resolveToIds(id, scope, filter).map {
            create(it, false)
        }
        return result
    }

    fun directDependencies(id: String, scope: Scope? = null): CollectResult? {
        val result = system.collectDependencies(session, createCollectRequest(id, scope))
        return result
    }

    fun directDependencies(artifact: Artifact, scope: Scope? = null): CollectResult?
        = artifactToId(artifact).let { id ->
            directDependencies(id, scope)
        }

    private fun resolveVersion(artifact: Artifact): VersionRangeResult? {
        val request = VersionRangeRequest(artifact, kobaltRepositories, null)
        val result = system.resolveVersionRange(session, request)
        return result
    }

    private fun latestMavenArtifact(group: String, artifactId: String, extension: String = "jar"): DependencyNode {
        val artifact = DefaultArtifact(group, artifactId, extension, "(0,]")
        val resolved = resolveVersion(artifact)
        if (resolved != null) {
            val newArtifact = DefaultArtifact(artifact.groupId, artifact.artifactId, artifact.extension,
                    resolved.highestVersion.toString())
            val artifactResult = resolve(artifactToId(newArtifact), null)
            return artifactResult
//            if (artifactResult != null) {
//                return artifactResult
//            } else {
//                throw KobaltException("Couldn't find latest artifact for $group:$artifactId")
//            }
        } else {
            throw KobaltException("Couldn't find latest artifact for $group:$artifactId")
        }
    }

    fun latestArtifact(group: String, artifactId: String, extension: String = "jar"): DependencyResult
            = latestMavenArtifact(group, artifactId, extension).let {
        DependencyResult(AetherDependency(it.artifact), "(TBD repo)")
    }

    /**
     * Create an IClasspathDependency from a Kobalt id.
     */
    fun create(id: String, optional: Boolean) = AetherDependency(DefaultArtifact(id), optional)

    private val system = Booter.newRepositorySystem()
    private val session = Booter.newRepositorySystemSession(system, localRepo.localRepo, settings, eventBus)

    private val kobaltRepositories: List<RemoteRepository>
        get() = Kobalt.repos.map {
            RemoteRepository.Builder(null, "default", it.url)
//                    .setSnapshotPolicy(RepositoryPolicy(false, null, null))
                    .build().let { repository ->
                val proxyConfigs = settings.proxyConfigs ?: return@map repository
                RemoteRepository.Builder(repository).apply {
                    setProxy(proxyConfigs.getProxy(repository.protocol)?.toAetherProxy())
                }.build()
            }
        }

    private fun createCollectRequest(id: String, scope: Scope? = null) = CollectRequest().apply {
        root = Dependency(DefaultArtifact(MavenId.toKobaltId(id)), scope?.scope)
        repositories = kobaltRepositories
    }
}
