package com.beust.kobalt.maven.aether

import com.beust.kobalt.Args
import com.beust.kobalt.api.Dependencies
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.misc.StringVersion
import com.beust.kobalt.misc.warn
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.resolution.DependencyResolutionException
import java.io.File
import java.util.concurrent.Future

class AetherDependency(val artifact: Artifact, override val optional: Boolean = false, val args: Args? = null)
        : IClasspathDependency, Comparable<AetherDependency> {
    val aether: KobaltMavenResolver get() = Kobalt.INJECTOR.getInstance(KobaltMavenResolver::class.java)

    override val id: String = toId(artifact)

    override val version: String = artifact.version

    override val isMaven = true

    private fun toId(a: Artifact) = a.toString()

    override val jarFile: Future<File>
        get() =
            if (artifact.file != null) {
                resolveSourcesIfNeeded()
                CompletedFuture(artifact.file)
            } else {
                resolveSourcesIfNeeded()
                val td = aether.resolve(artifact)
                CompletedFuture(td.root.artifact.file)
            }

    private fun resolveSourcesIfNeeded() {
        if (args?.downloadSources ?: false) {
            artifact.toSourcesArtifact().let { sourcesArtifact ->
                if (sourcesArtifact.file == null) {
                    try {
                        aether.resolve(sourcesArtifact)
                    } catch(e: DependencyResolutionException) {
                        //do nothing
                    }
                }
            }
            artifact.toJavaDocArtifact().let { javadocArtifact ->
                if (javadocArtifact.file == null) {
                    try {
                        aether.resolve(javadocArtifact)
                    } catch(e: DependencyResolutionException) {
                        //do nothing
                    }
                }
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
            deps.root.children.forEach {
                result.add(AetherDependency(it.artifact, it.dependency.optional))
            }
        } else {
            warn("Couldn't resolve $artifact")
        }
        return result
    }

    override val shortId = artifact.groupId + ":" + artifact.artifactId + ":" + artifact.classifier

    override val excluded = arrayListOf<Dependencies.ExcludeConfig>()

    override fun compareTo(other: AetherDependency): Int {
        return StringVersion(artifact.version).compareTo(StringVersion(other.artifact.version))
    }

    override fun hashCode() = id.hashCode()

    override fun equals(other: Any?) = if (other is AetherDependency) other.id == id else false

    override fun toString() = id

    fun Artifact.toSourcesArtifact() = DefaultArtifact(groupId, artifactId, "sources", extension, version)
    fun Artifact.toJavaDocArtifact() = DefaultArtifact(groupId, artifactId, "javadoc", extension, version)
}
