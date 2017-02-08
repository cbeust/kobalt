package com.beust.kobalt.maven.aether

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.maven.LocalDep
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.misc.Versions
import com.beust.kobalt.misc.warn
import org.eclipse.aether.artifact.Artifact
import java.io.File
import java.util.concurrent.Future

class AetherDependency(val artifact: Artifact, override val optional: Boolean = false)
        : IClasspathDependency, Comparable<AetherDependency> {
    val aether: KobaltMavenResolver get() = Kobalt.INJECTOR.getInstance(KobaltMavenResolver::class.java)

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
                CompletedFuture(td.root.artifact.file)
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

    override fun compareTo(other: AetherDependency): Int {
        return Versions.toLongVersion(artifact.version).compareTo(Versions.toLongVersion(
                other.artifact.version))
    }

    override fun hashCode() = id.hashCode()

    override fun equals(other: Any?) = if (other is AetherDependency) other.id == id else false

    override fun toString() = id
}
