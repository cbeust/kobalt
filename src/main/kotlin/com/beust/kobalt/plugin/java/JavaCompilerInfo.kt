package com.beust.kobalt.plugin.java

import com.beust.kobalt.api.ICompilerInfo
import com.beust.kobalt.misc.KFiles
import com.google.inject.Singleton
import java.io.File

@Singleton
public class JavaCompilerInfo : ICompilerInfo {
    override val name = "java"

    override fun findManagedFiles(dir: File) : List<File> {
        val result = KFiles.findRecursively(dir, { it.endsWith(".java") })
                .map { File(it) }
        return result
    }

    override val defaultSourceDirectories = arrayListOf("src/main/java", "src/main/resources")

    override val defaultTestDirectories = arrayListOf("src/test/java", "src/test/resources")

    override val directive = "javaProject"

}
