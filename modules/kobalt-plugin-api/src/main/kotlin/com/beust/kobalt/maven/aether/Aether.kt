package com.beust.kobalt.maven.aether

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.getProxy
import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.maven.LocalDep
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.misc.Versions
import com.beust.kobalt.misc.kobaltLog
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
import org.eclipse.aether.repository.ArtifactRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.DependencyResolutionException
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.transfer.ArtifactNotFoundException
import org.eclipse.aether.util.artifact.JavaScopes
import java.io.File
import java.util.concurrent.Future

enum class Scope(val scope: String, val dependencyLambda: (Project) -> List<IClasspathDependency>) {
    COMPILE(JavaScopes.COMPILE, Project::compileDependencies),
    PROVIDED(JavaScopes.PROVIDED, Project::compileProvidedDependencies),
    SYSTEM(JavaScopes.SYSTEM, { project -> emptyList() }),
    RUNTIME(JavaScopes.RUNTIME, Project::compileRuntimeDependencies),
    TEST(JavaScopes.TEST, Project::testDependencies)
    ;

    companion object {
        fun toScopes(isTest: Boolean) = if (isTest) listOf(Scope.TEST, Scope.COMPILE) else listOf(Scope.COMPILE)

        /**
         * @return a lambda that extracts the correct dependencies from a project based on the scope
         * filters passed (excludes optional dependencies).
         */
        fun toDependencyLambda(scopes: Collection<Scope>) : (Project) -> List<IClasspathDependency> {
            val result = { project : Project ->
                val deps = scopes.fold(arrayListOf<IClasspathDependency>(),
                    { list: ArrayList<IClasspathDependency>, scope: Scope ->
                        list.addAll(scope.dependencyLambda(project).filter { ! it.optional })
                        list
                    })
                deps
            }

            return result
        }
    }
}

class DependencyResult(val dependency: IClasspathDependency, val repoUrl: String)

class AetherResult(val artifact: Artifact, val repository: ArtifactRepository)

class KobaltAether @Inject constructor (val settings: KobaltSettings, val aether: Aether) {
    companion object {
        fun isRangeVersion(id: String) = id.contains(",")
    }

    /**
     * Create an IClasspathDependency from a Kobalt id.
     */
    fun create(id: String, optional: Boolean) = AetherDependency(DefaultArtifact(id), optional)

    /**
     * @return the latest artifact for the given group and artifactId.
     */
    fun latestArtifact(group: String, artifactId: String, extension: String = "jar"): DependencyResult
            = aether.latestArtifact(group, artifactId, extension).let {
        DependencyResult(AetherDependency(it.artifact), it.repository.toString())
    }

    fun resolveAll(id: String, artifactScope: Scope? = null, dependencyFilter: DependencyFilter?)
            : List<String> {
        val results = aether.resolve(DefaultArtifact(id), artifactScope, dependencyFilter)
        return results.map { it.artifact.toString() }
    }

    fun resolve(id: String, artifactScope: Scope? = null, dependencyFilter: DependencyFilter = Filters.COMPILE_FILTER)
            : DependencyResult {
        kobaltLog(ConsoleRepositoryListener.LOG_LEVEL, "Resolving $id")
        val result = resolveToArtifact(id, artifactScope, dependencyFilter)
        if (result != null) {
            return DependencyResult(AetherDependency(result.artifact), result.repository.toString())
        } else {
            throw KobaltException("Couldn't resolve $id")
        }
    }

    fun resolveToArtifact(id: String, artifactScope: Scope? = null,
            dependencyFilter: DependencyFilter? = null)
            : AetherResult? {
        kobaltLog(ConsoleRepositoryListener.LOG_LEVEL, "Resolving $id")
        val results = aether.resolve(DefaultArtifact(MavenId.toKobaltId(id)), artifactScope, dependencyFilter)
        if (results.size > 0) {
            return results[0]
        } else {
            return null
        }
    }
}

@Singleton
class Aether(localRepo: File, val settings: KobaltSettings, eventBus: EventBus) {
    private val system = Booter.newRepositorySystem()
    private val session = Booter.newRepositorySystemSession(system, localRepo, settings, eventBus)

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

    private fun rangeRequest(a: Artifact): VersionRangeRequest
        = VersionRangeRequest(a, kobaltRepositories, "RELEASE")

    private fun collectRequest(artifact: Artifact, scope: Scope?): CollectRequest {
        with(CollectRequest()) {
            root = Dependency(artifact, scope?.scope)
            repositories = kobaltRepositories

            return this
        }
    }

    fun latestArtifact(group: String, artifactId: String, extension: String = "jar"): AetherResult {
        val artifact = DefaultArtifact(group, artifactId, extension, "(0,]")
        val resolved = resolveVersion(artifact)
        if (resolved != null) {
            val newArtifact = DefaultArtifact(artifact.groupId, artifact.artifactId, artifact.extension,
                    resolved.highestVersion.toString())
            val artifactResult = resolve(newArtifact, null)
            if (artifactResult.any()) {
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

    fun resolve(artifact: Artifact, artifactScope: Scope?,
            dependencyFilter: DependencyFilter? = null)
            : List<AetherResult> {
        fun manageException(ex: Exception, artifact: Artifact): List<AetherResult> {
            if (artifact.extension == "pom") {
                // Only display a warning for .pom files. Not resolving a .jar or other artifact
                // is not necessarily an error as long as there is a pom file.
                warn("Couldn't resolve $artifact")
            }
            return emptyList()
        }

        try {
            val result =
                if (KobaltAether.isRangeVersion(artifact.version)) {
                    val request = rangeRequest(artifact)
                    val v = system.resolveVersionRange(session, request)
                    if (v.highestVersion != null) {
                        val highestVersion = v.highestVersion.toString()
                        val ar = DefaultArtifact(artifact.groupId, artifact.artifactId, artifact.classifier,
                                artifact.extension, highestVersion)
                        listOf(AetherResult(ar, request.repositories[0]))
                    } else {
                        throw KobaltException("Couldn't resolve range artifact " + artifact)
                    }
                } else {
                    val dependencyRequest = DependencyRequest(collectRequest(artifact, artifactScope), dependencyFilter)

                    try {
                        system.resolveDependencies(session, dependencyRequest).artifactResults.map {
                            AetherResult(it.artifact, it.repository)
                        }
                    } catch(ex: Exception) {
                        throw KobaltException("Couldn't resolve $artifact", ex)
                    }
                }
            return result
        } catch(ex: ArtifactNotFoundException) {
            return manageException(ex, artifact)
        } catch(ex: DependencyResolutionException) {
            return manageException(ex, artifact)
        }
    }

//    fun transitiveDependencies(artifact: Artifact) = directDependencies(artifact)

    fun directDependencies(artifact: Artifact, artifactScope: Scope? = null): CollectResult?
            = system.collectDependencies(session, collectRequest(artifact, artifactScope))
}

class AetherDependency(val artifact: Artifact, override val optional: Boolean = false)
        : IClasspathDependency, Comparable<AetherDependency> {
    val aether: Aether get() = Kobalt.INJECTOR.getInstance(Aether::class.java)

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
                val td = aether.resolve(artifact, null)
                if (td.any()) {
                    val newFile = td[0].artifact.file
                    if (newFile != null) {
                        CompletedFuture(newFile)
                    } else {
                        CompletedFuture(File("DOESNOTEXIST $id")) // will be filtered out
                    }
                } else {
                    CompletedFuture(File("DOESNOTEXIST $id"))
                }
            }
        }

    override fun toMavenDependencies(scope: String?) : org.apache.maven.model.Dependency {
        val passedScope = scope
        val op = this.optional
        return org.apache.maven.model.Dependency().apply {
            groupId = artifact.groupId
            artifactId = artifact.artifactId
            version = artifact.version
            if (op) optional = op.toString()
            if (passedScope != null) this.scope = passedScope
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
                        kobaltLog(ConsoleRepositoryListener.LOG_LEVEL,
                                "Skipping optional dependency " + deps.root.artifact)
                    }
                }
            } else {
                kobaltLog(ConsoleRepositoryListener.LOG_LEVEL, "Skipping optional dependency " + deps.root.artifact)
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

fun f(argv: Array<String>) {
    val collectRequest = CollectRequest().apply {
        root = Dependency(DefaultArtifact("com.squareup.retrofit2:converter-jackson:jar:2.1.0"), JavaScopes.COMPILE)
        repositories = listOf(
//                RemoteRepository.Builder("Maven", "default", "http://repo1.maven.org/maven2/").build()
                RemoteRepository.Builder("JCenter", "default", "https://jcenter.bintray.com").build()
        )
    }
//    val dependencyRequest = DependencyRequest().apply {
//        collectRequest = request
//        filter = object: DependencyFilter {
//            override fun accept(p0: DependencyNode, p1: MutableList<DependencyNode>?): Boolean {
//                if (p0.artifact.artifactId.contains("android")) {
//                    println("ANDROID")
//                }
//                return p0.dependency.scope == JavaScopes.COMPILE
//            }
//
//        }
//    }
    val dr2 = DependencyRequest(collectRequest, null).apply {}


//    val system = ManualRepositorySystemFactory.newRepositorySystem()
//    val session = DefaultRepositorySystemSession()
//    val localRepo = LocalRepository(File("/Users/cedricbeust/t/localAether").absolutePath)
//    session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)

    val system = Booter.newRepositorySystem()
    val session = Booter.newRepositorySystemSession(system)

    val result = system.resolveDependencies(session, dr2).artifactResults
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

fun f2() {
    val system = Booter.newRepositorySystem()

    val session = Booter.newRepositorySystemSession(system)

    val artifact = DefaultArtifact("com.squareup.retrofit2:converter-jackson:jar:2.1.0")

//        DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter( JavaScopes.COMPILE );
    val f2 = DependencyFilter { dependencyNode, list ->
        println("ACCEPTING " + dependencyNode)
        true
    }

    val collectRequest = CollectRequest()
    collectRequest.root = Dependency(artifact, JavaScopes.COMPILE)
    collectRequest.repositories = listOf(
        RemoteRepository.Builder("Maven", "default", "http://repo1.maven.org/maven2/").build()
    )

    val dependencyRequest = DependencyRequest(collectRequest, null)

    val artifactResults = system.resolveDependencies(session, dependencyRequest).artifactResults

    for (artifactResult in artifactResults) {
        println(artifactResult.artifact.toString() + " resolved to " + artifactResult.artifact.file)
    }
}


fun main(args: Array<String>) {
    f2()
}

