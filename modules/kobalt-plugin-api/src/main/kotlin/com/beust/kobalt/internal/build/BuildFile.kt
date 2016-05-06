package com.beust.kobalt.internal.build

import com.beust.kobalt.misc.KFiles
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
/**
 * Sometimes, build files are moved to temporary files, so we give them a specific name for clarity.
 * @param path is the path where that file was moved, @param realPath is where the actual file is.
 */
class BuildFile(val path: Path, val name: String, val realPath: Path = path) {
    fun exists() : Boolean = Files.exists(path)

    val lastModified : Long
        get() = Files.readAttributes(realPath, BasicFileAttributes::class.java).lastModifiedTime().toMillis()

    val directory : File get() = path.toFile().parentFile

    /**
     * @return the .kobalt directory where this build file will be compiled.
     */
    val dotKobaltDir: File get() = File(directory.parentFile.parentFile, KFiles.KOBALT_DOT_DIR).apply {
        mkdirs()
    }

    /**
     * @return the absolute directory of this projects' location, assuming the build file is in
     * $project/kobalt/src/Build.kt.
     */
    val absoluteDir = path.parent.parent.parent.toFile()
}
