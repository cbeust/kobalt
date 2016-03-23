package com.beust.kobalt.maven.aether

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.internal.KobaltSettings
import com.google.inject.Inject
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.util.artifact.JavaScopes
import org.eclipse.aether.util.filter.DependencyFilterUtils
import java.io.File

class Aether @Inject constructor(val settings: KobaltSettings){
    fun call3() {
        println("------------------------------------------------------------")

        val system = Booter.newRepositorySystem()

        val session = Booter.newRepositorySystemSession(system, File(settings.localRepo))

        val artifact = DefaultArtifact("org.testng:testng:6.9.9")

        val classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE)

        val collectRequest = CollectRequest()
        collectRequest.root = Dependency(artifact, JavaScopes.COMPILE)
        collectRequest.repositories = Booter.newRepositories(Kobalt.repos.map { it.url })

        val dependencyRequest = DependencyRequest(collectRequest, classpathFlter)

        val artifactResults = system.resolveDependencies(session, dependencyRequest).artifactResults

        for (artifactResult in artifactResults) {
            println(artifactResult.artifact.toString() + " resolved to " + artifactResult.artifact.file)
        }
    }

    //    fun call2() {
    //        val request = ArtifactRequest().apply {
    //            artifact = DefaultArtifact(id)
    //            repositories = listOf(RemoteRepository("Maven", "", repo.url))
    //        }
    //        val repoSystem = DefaultRepositorySystem().apply {
    //            val artifactResolver = DefaultArtifactResolver().apply {
    //                setRemoteRepositoryManager(DefaultRemoteRepositoryManager().apply {
    //                    addRepositoryConnectorFactory(WagonRepositoryConnectorFactory())
    //                })
    //                setVersionResolver {
    //                    p0, request -> VersionResult(request)
    //                }
    //            }
    //            setArtifactResolver(artifactResolver)
    //        }
    //        val session = DefaultRepositorySystemSession().apply {
    //            localRepositoryManager = SimpleLocalRepositoryManager(File("/Users/beust/.aether"))
    //        }
    //        val artifact = repoSystem.resolveArtifact(session, request)
    //        println("Artifact: " + artifact)
    //    }


}
