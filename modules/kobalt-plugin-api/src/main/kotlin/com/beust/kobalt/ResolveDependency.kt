package com.beust.kobalt

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.maven.RepoFinder
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.Node
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.util.*

/**
 * Display information about a Maven id.
 */
class ResolveDependency @Inject constructor(val repoFinder: RepoFinder, val localRepo: LocalRepo) {
    val increment = 8
    val leftFirst = "\u2558"
    val leftMiddle = "\u255f"
    val leftLast = "\u2559"
    val vertical = "\u2551"

    class Dep(val dep: IClasspathDependency, val level: Int)

    fun run(id: String) {
        val repoResult = repoFinder.findCorrectRepo(id)

        val indent = -1
        val originalDep = MavenDependency.create(id)
        val mavenId = MavenId.create(id)
        val packaging = if (mavenId.packaging != null) "@" + mavenId.packaging else ""
        // We want to display the dependencies of the id we found, not the one we queries
        val dep = MavenDependency.create(originalDep.shortId + repoResult.version + packaging)
        val root = Node(Dep(dep, indent))
        val seen = hashSetOf(id)
        root.addChildren(findChildren(root, seen))

        val url = repoResult.hostConfig.url + repoResult.path
        val localFile = localRepo.toFullPath(repoResult.path!!)
        AsciiArt.logBox(listOf(id, url, localFile).map { "          $it" }, {s -> println(s) })

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

