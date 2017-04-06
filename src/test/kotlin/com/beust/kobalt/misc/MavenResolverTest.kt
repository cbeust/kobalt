package com.beust.kobalt.misc

import com.beust.kobalt.BaseTest
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.KobaltSettingsXml
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.aether.Booter
import com.beust.kobalt.maven.aether.KobaltMavenResolver
import com.google.common.eventbus.EventBus
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.DependencyResolutionException
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class MavenResolverTest : BaseTest() {
    @Inject
    lateinit var resolver: KobaltMavenResolver

    @Inject
    lateinit var dependencyManager: DependencyManager

    @Inject
    lateinit var localRepo: LocalRepo

    @DataProvider
    fun rangeProvider() = arrayOf(
            arrayOf("org.sql2o:sql2o:[1.5.0,1.5.1]", "1.5.1"),
            arrayOf("org.sql2o:sql2o:[1.5.0,)", "1.6.0-RC3"), // can potentially fail
            arrayOf("org.sql2o:sql2o:[1.5.0,1.5.1)", "1.5.0")
    )

    @Test(dataProvider = "rangeProvider")
    fun rangeVersion(id: String, expectedVersion: String) {
        val result = resolve(id)
        assertThat(result.size).isGreaterThan(0)
        assertThat(result[0].artifact.version).isEqualTo(expectedVersion)
    }

    @Test(dataProvider = "rangeProvider", groups = arrayOf("mavenResolverBug"))
    fun kobaltRangeVersion(id: String, expectedVersion: String) {
        val artifact = resolver.resolveToArtifact(id)
        assertThat(artifact.version).isEqualTo(expectedVersion)
    }

    @Test
    fun aetherShouldNotIncludeOptionalDependencies() {
        val artifactResults = resolve("com.squareup.retrofit2:converter-jackson:jar:2.1.0")

        // Make sure that com.google.android is not included (it's an optional dependency of retrofit2)
        assertThat(artifactResults.none { it.toString().contains("android") })
    }

    @Test
    fun kobaltAetherShouldNotIncludeOptionalDependencies() {
        val dep = resolver.create("com.squareup.retrofit2:converter-jackson:jar:2.1.0", optional = false)
        val closure = dependencyManager.transitiveClosure(listOf(dep))

        // Make sure that com.google.android is not included (it's an optional dependency of retrofit2)
        assertThat(closure.none { it.toString().contains("android") })
    }

    @Test
    fun shouldResolveSnapshots() {
        try {
            // Should throw
            resolver.resolve("org.bukkit:bukkit:1.11.2-R0.1-SNAPSHOT")
        } catch(ex: DependencyResolutionException) {
            // Success. Note: run the failing test first, because once the resolve succeeds, its
            // results are cached in the local repo.
        }

        // Should succeed
        resolver.resolve("org.bukkit:bukkit:1.11.2-R0.1-SNAPSHOT",
                repos = listOf("https://hub.spigotmc.org/nexus/content/repositories/snapshots"))
    }

    private fun resolve(id: String): List<ArtifactResult> {
        val system = Booter.newRepositorySystem()
        val session = Booter.newRepositorySystemSession(system,
                localRepo.localRepo, KobaltSettings(KobaltSettingsXml()), EventBus())
        val artifact = DefaultArtifact(id)

        val collectRequest = CollectRequest().apply {
            root = Dependency(artifact, null)
            repositories = listOf(
                    RemoteRepository.Builder("Maven", "default", "http://repo1.maven.org/maven2/").build(),
                    RemoteRepository.Builder("JCenter", "default",  "http://jcenter.bintray.com").build()
            )
        }

        val dependencyRequest = DependencyRequest(collectRequest, null)

        val result = system.resolveDependencies(session, dependencyRequest).artifactResults
        return result
    }
}

//fun main(args: Array<String>) {
//    val system = Booter.newRepositorySystem()
//    val settings = KobaltSettings(KobaltSettingsXml()).apply {
//        localCache = File(homeDir(".kobalt/cache"))
//    }
//
//    val session = Booter.newRepositorySystemSession(system,
//            LocalRepo(settings).localRepo, settings, EventBus())
//
//    val id = "com.sparkjava:spark-core:jar:2.5"
//    val artifact = DefaultArtifact(id)
//
//    val collectRequest = CollectRequest().apply {
//        root = Dependency(artifact, null)
//        repositories = listOf(
//                RemoteRepository.Builder("Maven", "default", "http://repo1.maven.org/maven2/").build(),
//                RemoteRepository.Builder("JCenter", "default",  "http://jcenter.bintray.com").build()
//        )
//    }
//
//    val dependencyRequest = DependencyRequest(collectRequest, null)
//    val result = system.resolveDependencies(session, dependencyRequest).artifactResults
//    println("Dependencies for $id:"+ result)
////    val result2 = system.resolveArtifacts(session, listOf(dependencyRequest))
////    println("Artifacts for $id:" + result)
////    GraphUtil.displayGraph(result, {a: ArtifactResult -> a.artifact
//}