package com.beust.kobalt.internal.build

import com.beust.kobalt.homeDir
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

interface IBuildSources {
    fun findSourceFiles() : List<File>
    val root: File
    fun exists(): Boolean
}

class SingleFileBuildSources(val file: File) : IBuildSources {
    override fun exists() = file.exists()
    override fun findSourceFiles() = listOf(file)
    override val root: File = file.parentFile.parentFile.parentFile
}

class BuildSources(val file: File = File("")) : IBuildSources {

    override val root = file

    override fun findSourceFiles() : List<File> {
        return findBuildFiles(listOf(file))
    }

    override fun exists() = findSourceFiles().isNotEmpty()

    override fun toString() = "{BuildSources " + findSourceFiles().joinToString(", ") + "}"

    fun findBuildFiles(roots: List<File>) : List<File> {
        val result = arrayListOf<File>()
        roots.forEach { file ->
            Files.walkFileTree(Paths.get(file.path), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                if (dir != null) {
                    val path = dir.toFile()
                    if (path.name == "src" && path.parentFile.name == "kobalt") {
                            val sources = path.listFiles().filter { it.name.endsWith(".kt") }
                        result.addAll(sources)
                    }
                }

                return FileVisitResult.CONTINUE
            }
        })
        }
        return result
    }
}

fun main(args: Array<String>) {
    val sources = BuildSources(File(homeDir("kotlin/kobalt"))).findSourceFiles()
    println("sources: " + sources)
}