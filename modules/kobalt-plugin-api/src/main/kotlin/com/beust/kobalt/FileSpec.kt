package com.beust.kobalt

import com.beust.kobalt.misc.log
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Subclasses of IFileSpec can be turned into a list of files. There are two kings: FileSpec (a single file)
 * and GlobSpec (a spec defined by a glob, e.g. ** slash *Test.class)
 */
sealed class IFileSpec {
    abstract fun toFiles(filePath: String, excludes: List<Glob> = emptyList<Glob>()): List<File>

    class FileSpec(val spec: String) : IFileSpec() {
        override public fun toFiles(filePath: String, excludes: List<Glob>) = listOf(File(spec))

        override public fun toString() = spec
    }

    class GlobSpec(val spec: List<String>) : IFileSpec() {

        constructor(spec: String) : this(arrayListOf(spec))

        private fun isIncluded(includeMatchers: Glob, excludes: List<Glob>, rel: Path) : Boolean {
            excludes.forEach {
                if (it.matches(rel)) {
                    log(2, "Excluding ${rel.toFile()}")
                    return false
                }
            }
            if (includeMatchers.matches(rel)) {
                log(2, "Including ${rel.toFile()}")
                return true
            }
            log(2, "Excluding ${rel.toFile()} (not matching any include pattern")
            return false
        }

        override public fun toFiles(filePath: String, excludes: List<Glob>): List<File> {
            val result = arrayListOf<File>()
            val includes = Glob(*spec.toTypedArray())

            if (File(filePath).isDirectory) {
                Files.walkFileTree(Paths.get(filePath), object : SimpleFileVisitor<Path>() {
                    override public fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val rel = Paths.get(filePath).relativize(path)
                        if (isIncluded(includes, excludes, rel)) {
                            result.add(rel.toFile())
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
            } else {
                if (isIncluded(includes, excludes, Paths.get(filePath))) {
                    result.add(File(filePath))
                }
            }

            return result
        }

        override public fun toString(): String {
            var result = ""
            spec.apply {
                if (!isEmpty()) {
                    result += "Included files: " + joinToString { ", " }
                }
            }
            return result
        }
    }

}

/**
 * A Glob is a simple file name matcher.
 */
class Glob(vararg specs: String) {
    val matchers = prepareMatchers(specs.toList())

    private fun prepareMatchers(specs: List<String>): List<PathMatcher> =
            specs.map { it -> FileSystems.getDefault().getPathMatcher("glob:$it") }

    fun matches(s: String) = matches(Paths.get(s))

    fun matches(path: Path) = matchers.any { it.matches(path) }
}
