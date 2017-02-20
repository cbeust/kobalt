package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import java.io.*
import java.net.URL
import java.util.jar.JarInputStream

/**
 * Base class for templates that decompress a jar file.
 */
interface InputStreamJarTemplate : ITemplate {
    val inputStream: InputStream

    override fun generateTemplate(args: Args, classLoader: ClassLoader) {
        val destDir = File(".")
        JarInputStream(inputStream).use { ins ->
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
}

abstract class ResourceJarTemplate(jarName: String, val classLoader: ClassLoader) : InputStreamJarTemplate {
    override val inputStream : InputStream = classLoader.getResource(jarName).openConnection().inputStream
}

abstract class FileJarTemplate(val fileName: String) : InputStreamJarTemplate {
    override val inputStream = FileInputStream(File(fileName))
}

abstract class HttpJarTemplate(val url: String) : InputStreamJarTemplate {
    override val inputStream : InputStream
        get() {
            try {
                return URL(url).openConnection().inputStream
            } catch(ex: IOException) {
                throw IllegalArgumentException("Couldn't connect to $url")
            }
        }
}
