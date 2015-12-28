package com.beust.kobalt.internal.build

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Sometimes, build files are moved to temporary files, so we give them a specific name for clarity.
 * @param path is the path where that file was moved, @param realPath is where the actual file is.
 */
class BuildFile(val path: Path, val name: String, val realPath: Path = path) {
    public fun exists(): Boolean = Files.exists(path)

    public val lastModified: Long
        get() = Files.readAttributes(realPath, BasicFileAttributes::class.java).lastModifiedTime().toMillis()

    public val directory: File get() = path.toFile().directory
}
