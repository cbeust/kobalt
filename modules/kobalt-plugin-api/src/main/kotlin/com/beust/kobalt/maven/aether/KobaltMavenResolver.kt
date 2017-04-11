package com.beust.kobalt.maven.aether

import com.beust.kobalt.Args
import com.beust.kobalt.HostConfig
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
import java.util.*

class KobaltMavenResolver @Inject constructor(val settings: KobaltSettings,
        val args: Args,
        localRepo: LocalRepo, eventBus: EventBus) {

    companion object {
        fun artifactToId(artifact: Artifact) = artifact.let {
            MavenId.toId(it.groupId, it.artifactId, it.extension, it.classifier, it.version)
        }
        fun isRangeVersion(id: String) = id.contains(",")
    }

    fun resolveToArtifact(id: String, scope: Scope? = null,
            filter: DependencyFilter = Filters.EXCLUDE_OPTIONAL_FILTER) : Artifact
        = resolve(id, scope, filter).root.artifact

    fun resolve(passedId: String, scope: Scope? = null,
            filter: DependencyFilter = Filters.EXCLUDE_OPTIONAL_FILTER,
            repos: List<String> = emptyList()): DependencyResult {
        val mavenId = MavenId.toMavenId(passedId)
        val id =
            if (isRangeVersion(mavenId)) {
                val artifact = DefaultArtifact(mavenId)
                val request = VersionRangeRequest(artifact, createRepos(repos), null)
                val rr = system.resolveVersionRange(session, request)
                val newArtifact = DefaultArtifact(artifact.groupId, artifact.artifactId, artifact.classifier,
                        artifact.extension, rr.highestVersion.toString())
                artifactToId(newArtifact)
            } else {
                passedId
            }

        val collectRequest = createCollectRequest(id)
        val dependencyRequest = DependencyRequest(collectRequest, filter)
        val result = system.resolveDependencies(session, dependencyRequest)
        //        GraphUtil.displayGraph(listOf(result.root), { it -> it.children },
        //                { it: DependencyNode, indent: String -> println(indent + it.toString()) })
        return result
    }

    fun resolve(artifact: Artifact, scope: Scope? = null,
            filter: DependencyFilter = Filters.EXCLUDE_OPTIONAL_FILTER)
        = resolve(artifactToId(artifact), scope, filter)

    fun resolveToIds(id: String, scope: Scope? = null,
            filter: DependencyFilter = Filters.EXCLUDE_OPTIONAL_FILTER,
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
    fun create(id: String, optional: Boolean) = AetherDependency(DefaultArtifact(id), optional, args)

    private val system = Booter.newRepositorySystem()
    private val session = Booter.newRepositorySystemSession(system, localRepo.localRepo, settings, eventBus)

    private fun createRepo(hostConfig: HostConfig) =
            RemoteRepository.Builder(hostConfig.name, "default", hostConfig.url).build()

    private val kobaltRepositories: List<RemoteRepository>
        get() = Kobalt.repos.map {
            createRepo(it).let { repository ->
                val proxyConfigs = settings.proxyConfigs ?: return@map repository
                RemoteRepository.Builder(repository).apply {
                    setProxy(proxyConfigs.getProxy(repository.protocol)?.toAetherProxy())
                }.build()
            }
        }

    private fun createRepos(repos: List<String>) : List<RemoteRepository>
            = kobaltRepositories + repos.map { createRepo(HostConfig(it)) }

    private fun createCollectRequest(id: String, scope: Scope? = null, repos: List<String> = emptyList())
            = CollectRequest().apply {
        val allIds = arrayListOf(MavenId.toMavenId(id))

        dependencies = allIds.map { Dependency(DefaultArtifact(it), scope?.scope) }

        root = Dependency(DefaultArtifact(MavenId.toMavenId(id)), scope?.scope)
        repositories = createRepos(repos)
    }
}
