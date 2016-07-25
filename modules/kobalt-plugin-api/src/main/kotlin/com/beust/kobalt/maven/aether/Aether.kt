package com.beust.kobalt.maven.aether

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.KobaltSettingsXml
import com.beust.kobalt.internal.getProxy
import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.maven.LocalDep
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.misc.Versions
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.common.eventbus.EventBus
import com.google.inject.Inject
import com.google.inject.Singleton
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.CollectResult
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyFilter
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.*
import org.eclipse.aether.transfer.ArtifactNotFoundException
import org.eclipse.aether.util.artifact.JavaScopes
import org.eclipse.aether.util.filter.AndDependencyFilter
import org.eclipse.aether.util.filter.DependencyFilterUtils
import java.io.File
import java.util.concurrent.Future

class DependencyResult(val dependency: IClasspathDependency, val repoUrl: String)

class KobaltAether @Inject constructor (val settings: KobaltSettings, val aether: Aether) {
    val localRepo: File get() = settings.localCache

    /**
     * Create an IClasspathDependency from a Kobalt id.
     */
    fun create(id: String) = AetherDependency(DefaultArtifact(id))

    /**
     * @return the latest artifact for the given group and artifactId.
     */
    fun latestArtifact(group: String, artifactId: String, extension: String = "jar"): DependencyResult
            = aether.latestArtifact(group, artifactId, extension).let {
        DependencyResult(AetherDependency(it.artifact), it.repository.toString())
    }

    fun resolveAll(id: String, isTest: Boolean): List<String> {
        val results = aether.resolve(DefaultArtifact(id), isTest)
        return results.map { it.artifact.toString() }
    }

    fun resolve(id: String, isTest: Boolean = false): DependencyResult {
        log(ConsoleRepositoryListener.LOG_LEVEL, "Resolving $id")
        val result = resolveToArtifact(id, isTest)
        if (result != null) {
            return DependencyResult(AetherDependency(result.artifact), result.repository.toString())
        } else {
            throw KobaltException("Couldn't resolve $id")
        }
    }

    fun resolveToArtifact(id: String, isTest: Boolean = false): ArtifactResult? {
        log(ConsoleRepositoryListener.LOG_LEVEL, "Resolving $id")
        val results = aether.resolve(DefaultArtifact(MavenId.toKobaltId(id)), isTest)
        if (results.size > 0) {
            return results[0]
        } else {
            return null
        }
    }

    class ExcludeOptionalDependencyFilter : DependencyFilter {
        override fun accept(node: DependencyNode?, p1: MutableList<DependencyNode>?): Boolean {
//        val result = node != null && ! node.dependency.isOptional
            val accept1 = node == null || node.artifact.artifactId != "srczip"
            val accept2 = node != null && !node.dependency.isOptional
            val result = accept1 && accept2
            return result
        }
    }
}

@Singleton
class Aether(val localRepo: File, val settings: KobaltSettings, val eventBus: EventBus) {
    private val system = Booter.newRepositorySystem()
    private val session = Booter.newRepositorySystemSession(system, localRepo, settings, eventBus)
    private val classpathFilter = AndDependencyFilter(
            KobaltAether.ExcludeOptionalDependencyFilter(),
            DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE),
            DependencyFilterUtils.classpathFilter(JavaScopes.TEST))

    private val testClasspathFilter = AndDependencyFilter(
            KobaltAether.ExcludeOptionalDependencyFilter(),
            DependencyFilterUtils.classpathFilter(JavaScopes.TEST))

    private val kobaltRepositories: List<RemoteRepository>
        get() = Kobalt.repos.map {
            RemoteRepository.Builder("maven", "default", it.url)
//                    .setSnapshotPolicy(RepositoryPolicy(false, null, null))
                    .build().let { repository ->
                val proxyConfigs = settings.proxyConfigs ?: return@map repository
                RemoteRepository.Builder(repository).apply {
                    setProxy(proxyConfigs.getProxy(repository.protocol)?.toAetherProxy())
                }.build()
            }
        }

    private fun collectRequest(artifact: Artifact, isTest: Boolean): CollectRequest {
        with(CollectRequest()) {
            root = Dependency(artifact, if (isTest) JavaScopes.TEST else JavaScopes.COMPILE)
            repositories = kobaltRepositories

            return this
        }
    }

    fun latestArtifact(group: String, artifactId: String, extension: String = "jar"): ArtifactResult {
        val artifact = DefaultArtifact(group, artifactId, extension, "(0,]")
        val resolved = resolveVersion(artifact)
        if (resolved != null) {
            val newArtifact = DefaultArtifact(artifact.groupId, artifact.artifactId, artifact.extension,
                    resolved.highestVersion.toString())
            val artifactResult = resolve(newArtifact)
            if (artifactResult != null && artifactResult.size > 0) {
                return artifactResult[0]
            } else {
                throw KobaltException("Couldn't find latest artifact for $group:$artifactId")
            }
        } else {
            throw KobaltException("Couldn't find latest artifact for $group:$artifactId")
        }
    }

    fun resolveVersion(artifact: Artifact): VersionRangeResult? {
        val request = VersionRangeRequest(artifact, kobaltRepositories, null)
        val result = system.resolveVersionRange(session, request)
        return result
    }

    fun resolve(artifact: Artifact, isTest: Boolean = false): List<ArtifactResult> {
        fun manageException(ex: Exception, artifact: Artifact): List<ArtifactResult> {
            if (artifact.extension == "pom") {
                // Only display a warning for .pom files. Not resolving a .jar or other artifact
                // is not necessarily an error as long as there is a pom file.
                warn("Couldn't resolve $artifact")
            }
            return emptyList()
        }

        try {
            val dependencyRequest = DependencyRequest(collectRequest(artifact, isTest),
                    if (isTest) testClasspathFilter else classpathFilter)
            val result = system.resolveDependencies(session, dependencyRequest).artifactResults
            return result
        } catch(ex: ArtifactNotFoundException) {
            return manageException(ex, artifact)
        } catch(ex: DependencyResolutionException) {
            return manageException(ex, artifact)
        }
    }

    fun transitiveDependencies(artifact: Artifact) = directDependencies(artifact)

    fun directDependencies(artifact: Artifact, isTest: Boolean = false): CollectResult?
            = system.collectDependencies(session, collectRequest(artifact, isTest))
}

class AetherDependency(val artifact: Artifact) : IClasspathDependency, Comparable<AetherDependency> {
    val aether: Aether get() = Kobalt.INJECTOR.getInstance(Aether::class.java)

    constructor(node: DependencyNode) : this(node.artifact) {
    }

    override val id: String = toId(artifact)

    override val version: String = artifact.version

    override val isMaven = true

    private fun toId(a: Artifact) = a.toString()

    override val jarFile: Future<File>
        get() = if (artifact.file != null) {
            CompletedFuture(artifact.file)
        } else {
            val localRepo = Kobalt.INJECTOR.getInstance(LocalRepo::class.java)
            val file = File(LocalDep(MavenId.create(id), localRepo).toAbsoluteJarFilePath(version))
            if (file.exists()) {
                CompletedFuture(file)
            } else {
                val td = aether.resolve(artifact)
                val newFile = td[0].artifact.file
                if (newFile != null) {
                    CompletedFuture(newFile)
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

    override fun directDependencies(): List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        val deps = aether.directDependencies(artifact)
        if (deps != null) {
            if (!deps.root.dependency.optional) {
                deps.root.children.forEach {
                    if (!it.dependency.isOptional) {
                        result.add(AetherDependency(it.artifact))
                    } else {
                        log(ConsoleRepositoryListener.LOG_LEVEL, "Skipping optional dependency " + deps.root.artifact)
                    }
                }
            } else {
                log(ConsoleRepositoryListener.LOG_LEVEL, "Skipping optional dependency " + deps.root.artifact)
            }
        } else {
            warn("Couldn't resolve $artifact")
        }
        return result
    }

    override val shortId = artifact.groupId + ":" + artifact.artifactId + ":" + artifact.classifier

    override fun compareTo(other: AetherDependency): Int {
        return Versions.toLongVersion(artifact.version).compareTo(Versions.toLongVersion(
                other.artifact.version))
    }

    override fun hashCode() = id.hashCode()

    override fun equals(other: Any?) = if (other is AetherDependency) other.id == id else false

    override fun toString() = id
}

fun main(argv: Array<String>) {
    val request = CollectRequest().apply {
        root = Dependency(DefaultArtifact("org.testng:testng:6.9.11"), JavaScopes.COMPILE)
        repositories = listOf(
                RemoteRepository.Builder("Maven", "default", "http://repo1.maven.org/maven2/").build(),
                RemoteRepository.Builder("JCenter", "default", "https://jcenter.bintray.com").build())
    }
    val dependencyRequest = DependencyRequest().apply {
        collectRequest = request
    }
    val system = Booter.newRepositorySystem()
    val session = Booter.newRepositorySystemSession(system, File("/tmp"), KobaltSettings(KobaltSettingsXml()),
            EventBus())
//    val session = MavenRepositorySystemUtils.newSession(KobaltSettings(KobaltSettingsXml()))
    val result = system.resolveDependencies(session, dependencyRequest).artifactResults
    println("RESULT: " + result)

//    KobaltLogger.LOG_LEVEL = 1
//    val id = "org.testng:testng:6.9.11"
//    val aether = KobaltAether(KobaltSettings(KobaltSettingsXml()), Aether(File(homeDir(".aether")),
//            KobaltSettings(KobaltSettingsXml()), EventBus()))
//    val r = aether.resolve(id)
//    val r2 = aether.resolve(id)
//    val d = org.eclipse.aether.artifact.DefaultArtifact("org.testng:testng:6.9")
//
//    println("Artifact: " + d)
}

