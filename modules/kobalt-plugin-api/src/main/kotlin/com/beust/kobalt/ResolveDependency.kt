package com.beust.kobalt

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.maven.aether.KobaltAether
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.Node
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.util.*

/**
 * Display information about a Maven id.
 */
class ResolveDependency @Inject constructor(
        val localRepo: LocalRepo,
        val aether: KobaltAether,
        val executors: KobaltExecutors) {
    val increment = 8
    val leftFirst = "\u2558"
    val leftMiddle = "\u255f"
    val leftLast = "\u2559"
    val vertical = "\u2551"

    class Dep(val dep: IClasspathDependency, val level: Int)

    fun run(ids: List<String>) = ids.forEach { displayDependenciesFor(it) }

    private fun displayDependenciesFor(id: String) {
        val mavenId = MavenId.create(id)
        val resolved =
            if (mavenId.hasVersion) aether.resolve(id)
            else aether.latestArtifact(mavenId.groupId, mavenId.artifactId)

        displayDependencies(resolved.dependency, resolved.repoUrl)
    }

    private fun displayDependencies(dep: IClasspathDependency, url: String) {
        val indent = -1
        val root = Node(Dep(dep, indent))
        val seen = hashSetOf(dep.id)
        root.addChildren(findChildren(root, seen))

        AsciiArt.logBox(listOf(dep.id, url, dep.jarFile.get()).map { "          $it" }, print = {s -> println(s) })

        display(root.children)
        println("")
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
                    if (i % increment == 0) print(vertical)
                    else print(" ")
                }
                println(left + " " + dep.id)
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
                log(2, "Found dependency ${dep.dep.id} level: ${dep.level}")
                result.add(node)
                seen.add(it.id)
                node.addChildren(findChildren(node, seen))
            }
        }
        log(2, "Children for ${root.value.dep.id}: ${result.size}")
        return result
    }
}

