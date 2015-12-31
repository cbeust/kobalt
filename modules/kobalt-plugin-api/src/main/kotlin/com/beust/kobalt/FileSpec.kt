package com.beust.kobalt

import com.beust.kobalt.misc.log
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

sealed class IFileSpec {
    abstract fun toFiles(directory: String): List<File>

    class FileSpec(val spec: String) : IFileSpec() {
        override public fun toFiles(directory: String) = listOf(File(spec))

        override public fun toString() = spec
    }

    class GlobSpec(val spec: String) : IFileSpec() {
        override public fun toFiles(directory: String): List<File> {
            val result = arrayListOf<File>()

            val matcher = FileSystems.getDefault().getPathMatcher("glob:$spec")
            Files.walkFileTree(Paths.get(directory), object : SimpleFileVisitor<Path>() {
                override public fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val rel = Paths.get(directory).relativize(path)
                    if (matcher.matches(rel)) {
                        log(3, "Adding ${rel.toFile()}")
                        result.add(rel.toFile())
                    }
                    return FileVisitResult.CONTINUE
                }
            })
            return result
        }

        override public fun toString() = spec
    }

}
