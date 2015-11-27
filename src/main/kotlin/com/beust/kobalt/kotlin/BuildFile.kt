package com.beust.kobalt.kotlin

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Sometimes, build files are moved to temporary files, so we give them a specific name for clarity.
 */
public class BuildFile(val path: Path, val name: String) {
    public fun exists() : Boolean = Files.exists(path)

    public val lastModified : Long
        get() = Files.readAttributes(path, BasicFileAttributes::class.java).lastModifiedTime().toMillis()

    public val directory : File get() = path.toFile().directory
}
