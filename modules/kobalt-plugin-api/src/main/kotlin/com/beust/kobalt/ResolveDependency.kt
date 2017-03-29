package com.beust.kobalt

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.maven.aether.*
import com.beust.kobalt.misc.*
import com.google.inject.Inject
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.DependencyNode
import java.util.*

/**
 * Display information about a Maven id.
 */
class ResolveDependency @Inject constructor(
        val localRepo: LocalRepo,
        val aether: KobaltMavenResolver,
        val executors: KobaltExecutors) {
    val increment = 8
    val leftFirst = "\u2558"
    val leftMiddle = "\u255f"
    val leftLast = "\u2559"
    val vertical = "\u2551"

    class Dep(val dep: IClasspathDependency, val level: Int)

    fun run(id: String) = displayDependenciesFor(id)

    private fun latestMavenArtifact(group: String, artifactId: String, extension: String = "jar"): DependencyNode {
        val artifact = DefaultArtifact(group, artifactId, extension, "(0,]")
        val resolved = aether.resolveRange(artifact)
        if (resolved != null) {
            val newArtifact = DefaultArtifact(artifact.groupId, artifact.artifactId, artifact.extension,
                    resolved.highestVersion.toString())
            val artifactResult = aether.resolve(KobaltMavenResolver.artifactToId(newArtifact), null)
            return artifactResult.root
        } else {
            throw KobaltException("Couldn't find latest artifact for $group:$artifactId")
        }
    }

    class PairResult(val dependency: IClasspathDependency, val repoUrl: String)

    fun latestArtifact(group: String, artifactId: String, extension: String = "jar"): PairResult
            = latestMavenArtifact(group, artifactId, extension).let {
        PairResult(AetherDependency(it.artifact), "(TBD repo)")
    }

    private fun displayDependenciesFor(id: String) {
        val mavenId = MavenId.create(id)
        val resolved : PairResult =
            if (mavenId.hasVersion) {
                val node = aether.resolve(id, filter = Filters.EXCLUDE_OPTIONAL_FILTER)
                PairResult(AetherDependency(node.root.artifact), node.artifactResults[0].repository.toString())
            } else {
                latestArtifact(mavenId.groupId, mavenId.artifactId)
            }

        displayDependencies(resolved.dependency, resolved.repoUrl)
    }

    private fun displayDependencies(dep: IClasspathDependency, url: String) {
        val indent = -1
        val root = Node(Dep(dep, indent))
        val seen = hashSetOf(dep.id)
        root.addChildren(findChildren(root, seen))

        kobaltLog(1, AsciiArt.logBox(listOf(dep.id, url, dep.jarFile.get()).map { "          $it" }))

        display(root.children)
        kobaltLog(1, "")
    }

    private fun display(nodes: List<Node<Dep>>) {
        nodes.withIndex().forEach { indexNode ->
            val node = indexNode.value
            with(node.value) {
                val left =
                        if (indexNode.index == nodes.size - 1) leftLast
                        else leftMiddle
                val indent = level * increment
                for(i in 0..indent - 2) {
                    if (!KobaltLogger.isQuiet) {
                        if (i == 0 || ((i + 1) % increment == 0)) print(vertical)
                        else print(" ")
                    }
                }
                kobaltLog(1, left + " " + dep.id + (if (dep.optional) " (optional)" else ""))
                display(node.children)
            }
        }

    }

    private fun findChildren(root: Node<Dep>, seen: HashSet<String>): List<Node<Dep>> {
        val result = arrayListOf<Node<Dep>>()
        root.value.dep.directDependencies().forEach {
            if (! seen.contains(it.id)) {
                val dep = Dep(it, root.value.level + 1)
                val node = Node(dep)
                kobaltLog(2, "Found dependency ${dep.dep.id} level: ${dep.level}")
                result.add(node)
                seen.add(it.id)
                try {
                    node.addChildren(findChildren(node, seen))
                } catch(ex: Exception) {
                    if (! it.optional) warn("Couldn't resolve " + node)
                    // else don't warn about missing optional dependencies
                }
            }
        }
        kobaltLog(2, "Children for ${root.value.dep.id}: ${result.size}")
        return result
    }
}

