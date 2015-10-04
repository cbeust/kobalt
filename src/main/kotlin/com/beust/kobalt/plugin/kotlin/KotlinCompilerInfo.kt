package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.api.ICompilerInfo
import com.beust.kobalt.misc.KFiles
import java.io.File

public class KotlinCompilerInfo : ICompilerInfo {
    override val name = "kotlin"

    override fun findManagedFiles(dir: File): List<File> {
        val result = KFiles.findRecursively(dir, { it.endsWith(".kt") })
                .map { File(it) }
        return result
    }

    override val defaultSourceDirectories = arrayListOf("src/main/kotlin", "src/main/resources")

    override val defaultTestDirectories = arrayListOf("src/test/kotlin", "src/test/resources")

    override val directive = "javaProject"
}

