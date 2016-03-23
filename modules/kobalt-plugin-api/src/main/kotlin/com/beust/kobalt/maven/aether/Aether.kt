package com.beust.kobalt.maven.aether

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.homeDir
import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.log
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.CollectResult
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.util.artifact.JavaScopes
import org.eclipse.aether.util.filter.DependencyFilterUtils
import java.io.File
import java.util.concurrent.Future

class Aether(val localRepo: File = File(homeDir(".kobalt/repository"))) {
    fun transitiveDependencies(id: String): List<ArtifactResult>? {
        println("------------------------------------------------------------")

        val system = Booter.newRepositorySystem()

        val session = Booter.newRepositorySystemSession(system, localRepo)

        val artifact = DefaultArtifact(id)

        val classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE)

        val collectRequest = CollectRequest()
        collectRequest.root = Dependency(artifact, JavaScopes.COMPILE)
        collectRequest.repositories = Booter.newRepositories(Kobalt.repos.map { it.url })

        val dependencyRequest = DependencyRequest(collectRequest, classpathFlter)

        val result = system.resolveDependencies(session, dependencyRequest).artifactResults

        if (KobaltLogger.LOG_LEVEL > 1) {
            for (artifactResult in result) {
                log(2, artifactResult.artifact.toString() + " resolved to " + artifactResult.artifact.file)
            }
        }

        return result
    }

    fun directDependencies(id: String): CollectResult? {
        println("------------------------------------------------------------")

        val system = Booter.newRepositorySystem()

        val session = Booter.newRepositorySystemSession(system, localRepo)

        val artifact = DefaultArtifact(id)

        val classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE)

        val collectRequest = CollectRequest()
        collectRequest.root = Dependency(artifact, JavaScopes.COMPILE)
        collectRequest.repositories = Booter.newRepositories(Kobalt.repos.map { it.url })

        val result = system.collectDependencies(session, collectRequest)
        val root = result.root
        val icp = AetherDependency(root)
        println("Dep: " + root)
        return result
    }

    class AetherDependency(val root: DependencyNode): IClasspathDependency {
        override val id: String = toId(root.artifact)

        private fun toId(a: Artifact) = with(a) {
            groupId + ":" + artifactId + ":" + version
        }

        override val jarFile: Future<File>
            get() = CompletedFuture(root.artifact.file)

        override fun toMavenDependencies() = let { md ->
            org.apache.maven.model.Dependency().apply {
                root.artifact.let { md ->
                    groupId = md.groupId
                    artifactId = md.artifactId
                    version = md.version
                }
            }
        }

        override fun directDependencies() = root.children.map { AetherDependency(it) }

        override val shortId = root.artifact.groupId + ":" + root.artifact.artifactId
    }
}

fun main(argv: Array<String>) {
    val dd = Aether().directDependencies("org.testng:testng:6.9.9")
    println("DD: " + dd)
}
