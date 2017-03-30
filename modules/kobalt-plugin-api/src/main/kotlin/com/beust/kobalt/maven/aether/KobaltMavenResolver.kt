package com.beust.kobalt.maven.aether

import com.beust.kobalt.Args
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
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.DependencyResult
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult

class KobaltMavenResolver @Inject constructor(val settings: KobaltSettings,
        val args: Args,
        localRepo: LocalRepo, eventBus: EventBus) {

    companion object {
        fun artifactToId(artifact: Artifact) = artifact.let {
            MavenId.toId(it.groupId, it.artifactId, it.extension, it.classifier, it.version)
        }
        fun isRangeVersion(id: String) = id.contains(",")
    }

    fun resolveToArtifact(id: String, scope: Scope? = null, filter: DependencyFilter? = null) : Artifact
        = resolve(id, scope, filter).root.artifact

    fun resolve(id: String, scope: Scope? = null, filter: DependencyFilter? = null): DependencyResult {
        val dependencyRequest = DependencyRequest(createCollectRequest(id, scope), filter)
        val result = system.resolveDependencies(session, dependencyRequest)

//        GraphUtil.displayGraph(listOf(result.root), { it -> it.children },
//                { it: DependencyNode, indent: String -> println(indent + it.toString()) })
        return result
    }

    fun resolve(artifact: Artifact, scope: Scope? = null, filter: DependencyFilter? = null)
        = resolve(artifactToId(artifact), scope, filter)

    fun resolveToIds(id: String, scope: Scope? = null, filter: DependencyFilter? = null,
            seen: HashSet<String> = hashSetOf<String>()) : List<String> {
        val rr = resolve(id, scope, filter)
        val children =
            rr.root.children.filter {
                filter == null || filter.accept(DefaultDependencyNode(it.dependency), emptyList())
            }.filter {
                it.dependency.scope != Scope.SYSTEM.scope
            }
        val result = listOf(artifactToId(rr.root.artifact)) + children.flatMap {
                val thisId = artifactToId(it.artifact)
                if (! seen.contains(thisId)) {
                    seen.add(thisId)
                    resolveToIds(thisId, scope, filter, seen)
                } else {
                    emptyList()
                }
            }
        return result
    }

    fun directDependencies(id: String, scope: Scope? = null): CollectResult?
        = system.collectDependencies(session, createCollectRequest(id, scope))

    fun directDependencies(artifact: Artifact, scope: Scope? = null): CollectResult?
        = artifactToId(artifact).let { id ->
            directDependencies(id, scope)
        }

    fun resolveRange(artifact: Artifact): VersionRangeResult? {
        val request = VersionRangeRequest(artifact, kobaltRepositories, null)
        val result = system.resolveVersionRange(session, request)
        return result
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
        val allIds = arrayListOf(MavenId.toMavenId(id))
        if (args.downloadSources) {
            listOf("sources", "javadoc").forEach {
                val artifact = DefaultArtifact(id)
                val sourceArtifact = DefaultArtifact(artifact.groupId, artifact.artifactId, it, artifact.extension,
                        artifact.version)
                allIds.add(sourceArtifact.toString())
            }
        }
        dependencies = allIds.map { Dependency(DefaultArtifact(it), scope?.scope) }

        root = Dependency(DefaultArtifact(MavenId.toMavenId(id)), scope?.scope)
        repositories = kobaltRepositories
    }
}
