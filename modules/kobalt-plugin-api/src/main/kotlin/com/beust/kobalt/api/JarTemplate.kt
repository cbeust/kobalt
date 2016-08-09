package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarInputStream

/**
 * Base class for templates that simply decompress a jar file to generate their project.
 */
abstract class JarTemplate(val jarName: String) : ITemplate {
    companion object {
        fun extractFile(ins: JarInputStream, destDir: File) {
            var entry = ins.nextEntry
            while (entry != null) {
                val f = File(destDir.path + File.separator + entry.name)
                if (entry.isDirectory) {
                    f.mkdir()
                    entry = ins.nextEntry
                    continue
                }

                kobaltLog(2, "Extracting: $entry to ${f.absolutePath}")
                FileOutputStream(f).use { fos ->
                    KFiles.copy(ins, fos)
                }
                entry = ins.nextEntry
            }
        }
    }

    override fun generateTemplate(args: Args, classLoader: ClassLoader) {
        kobaltLog(2, "Generating template with class loader $classLoader")
        val destDir = File(".")
        val ins = JarInputStream(classLoader.getResource(jarName).openConnection().inputStream)
        extractFile(ins, destDir)
    }

}
