package com.beust.kobalt

import com.beust.kobalt.misc.log
import java.io.File
import java.nio.file.*
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

sealed class IFileSpec {
    abstract fun toFiles(directory: String): List<File>

    class FileSpec(val spec: String) : IFileSpec() {
        override public fun toFiles(directory: String) = listOf(File(spec))

        override public fun toString() = spec
    }

    class GlobSpec(val includeSpec: ArrayList<String>, val excludeSpec: ArrayList<String>) : IFileSpec() {

        constructor(spec: String) : this(arrayListOf(spec), arrayListOf())

        override public fun toFiles(directory: String): List<File> {

            val result = arrayListOf<File>()
            val includeMatchers = prepareMatchers(includeSpec.toTypedArray())
            val excludeMatchers = prepareMatchers(excludeSpec.toTypedArray())

            Files.walkFileTree(Paths.get(directory), object : SimpleFileVisitor<Path>() {
                override public fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {

                    val rel = Paths.get(directory).relativize(path)
                    excludeMatchers.forEach {
                        if (it.matches(rel)) {
                            log(3, "Excluding ${rel.toFile()}")
                            return CONTINUE
                        }
                    }
                    includeMatchers.forEach {
                        if (it.matches(rel)) {
                            log(3, "Including ${rel.toFile()}")
                            result.add(rel.toFile())
                            return CONTINUE
                        }
                    }

                    return CONTINUE
                }
            })
            return result
        }

        override public fun toString(): String {
            var result = ""
            includeSpec.apply {
                if (!isEmpty()) {
                    result += "Included files: " + joinToString { ", " }
                }
            }
            excludeSpec.apply {
                if (!isEmpty()) {
                    result += "Excluded files: " + joinToString { ", " }
                }
            }
            return result
        }

        private fun prepareMatchers(specs: Array<String>): List<PathMatcher> =
                specs.map { it -> FileSystems.getDefault().getPathMatcher("glob:$it") }
    }

}