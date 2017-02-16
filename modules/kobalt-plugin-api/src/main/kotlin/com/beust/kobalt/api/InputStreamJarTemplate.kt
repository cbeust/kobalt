package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.jar.JarInputStream

/**
 * Base class for templates that decompress a jar file.
 */
interface InputStreamJarTemplate : ITemplate {
    val inputStream: JarInputStream

    override fun generateTemplate(args: Args, classLoader: ClassLoader) {
        extractFile(inputStream, File("."))
    }

    private fun extractFile(ins: JarInputStream, destDir: File) {
        var entry = ins.nextEntry
        while (entry != null) {
            val f = File(destDir.path + File.separator + entry.name)
            if (entry.isDirectory) {
                f.mkdir()
                entry = ins.nextEntry
                continue
            }

            kobaltLog(2, "  Extracting: $entry to ${f.absolutePath}")
            FileOutputStream(f).use { fos ->
                KFiles.copy(ins, fos)
            }
            entry = ins.nextEntry
        }
    }
}

abstract class ResourceJarTemplate(val jarName: String, val classLoader: ClassLoader) : InputStreamJarTemplate {
    override val inputStream = JarInputStream(classLoader.getResource(jarName).openConnection().inputStream)
}

abstract class FileJarTemplate(val fileName: String, val classLoader: ClassLoader) : InputStreamJarTemplate {
    override val inputStream = JarInputStream(FileInputStream(File(fileName)))
}

abstract class HttpJarTemplate(val url: String, val classLoader: ClassLoader) : InputStreamJarTemplate {
    override val inputStream : JarInputStream
        get() {
            try {
                val result = URL(url).openConnection().inputStream
                return JarInputStream(result)
            } catch(ex: IOException) {
                throw IllegalArgumentException("Couldn't connect to $url")
            }
        }
}
