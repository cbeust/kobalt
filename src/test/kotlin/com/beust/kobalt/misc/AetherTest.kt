package com.beust.kobalt.misc

import com.beust.kobalt.TestModule
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.Booter
import com.beust.kobalt.maven.aether.KobaltAether
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.util.artifact.JavaScopes
import org.testng.annotations.Guice
import org.testng.annotations.Test

@Guice(modules = arrayOf(TestModule::class))
class AetherTest {
    @Inject
    lateinit var kobaltAether: KobaltAether

    @Inject
    lateinit var dependencyManager: DependencyManager

    @Test
    fun aetherShouldNotIncludeOptionalDependencies() {
        val system = Booter.newRepositorySystem()
        val session = Booter.newRepositorySystemSession(system)
        val artifact = DefaultArtifact("com.squareup.retrofit2:converter-jackson:jar:2.1.0")

        val collectRequest = CollectRequest().apply {
            root = Dependency(artifact, JavaScopes.COMPILE)
            repositories = listOf(
                    RemoteRepository.Builder("Maven", "default", "http://repo1.maven.org/maven2/").build()
            )
        }

        val dependencyRequest = DependencyRequest(collectRequest, null)

        val artifactResults = system.resolveDependencies(session, dependencyRequest).artifactResults

        // Make sure that com.google.android is not included (it's an optional dependency of retrofit2)
        assertThat(artifactResults.none { it.toString().contains("android") })
    }

    @Test
    fun kobaltAetherShouldNotIncludeOptionalDependencies() {
        val dep = kobaltAether.create("com.squareup.retrofit2:converter-jackson:jar:2.1.0", optional = false)
        val closure = dependencyManager.transitiveClosure(listOf(dep))

        // Make sure that com.google.android is not included (it's an optional dependency of retrofit2)
        assertThat(closure.none { it.toString().contains("android") })
    }
}
