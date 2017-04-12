package com.beust.kobalt.archive

import com.beust.kobalt.Glob
import com.beust.kobalt.misc.KFiles
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import org.apache.commons.compress.archivers.zip.ZipFile as ApacheZipFile

/**
 * Abstraction of a zip/jar/war archive that automatically manages the addition of expanded jar files.
 * Uses ZipArchiveOutputStream for fast inclusion of expanded jar files.
 */
class MetaArchive(outputFile: File, val manifest: java.util.jar.Manifest?) : Closeable {
    private val zos = ZipArchiveOutputStream(outputFile).apply {
        encoding = "UTF-8"
    }

    fun addFile(file: File, path: String) {
        FileInputStream(file).use { inputStream ->
            val entry = zos.createArchiveEntry(file, path)
            maybeAddEntry(entry) {
                addEntry(entry, inputStream)
            }
        }
    }

    fun addArchive(jarFile: File) {
        ApacheZipFile(jarFile).use { jar ->
            val jarEntries = jar.entries
            for (entry in jarEntries) {
                maybeAddEntry(entry) {
                    zos.addRawArchiveEntry(entry, jar.getRawInputStream(entry))
                }
            }
        }
    }

    private val DEFAULT_JAR_EXCLUDES =
            Glob("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    private val seen = hashSetOf<String>()

    private fun okToAdd(name: String): Boolean = ! seen.contains(name)
            && ! KFiles.isExcluded(name, DEFAULT_JAR_EXCLUDES)

    override fun close() {
        if (manifest != null) {
            Files.createTempFile("aaa", "bbb").toFile().let { manifestFile ->
                FileOutputStream(manifestFile).use { fos ->
                    manifest.write(fos)
                }

                val entry = zos.createArchiveEntry(manifestFile, "META-INF/MANIFEST.MF")
                addEntry(entry, FileInputStream(manifestFile))
            }
        }
        zos.close()
    }

    private fun addEntry(entry: ArchiveEntry, inputStream: FileInputStream) {
        zos.putArchiveEntry(entry)
        inputStream.use { ins ->
            ins.copyTo(zos, 50 * 1024)
        }
        zos.closeArchiveEntry()
    }

    private fun maybeAddEntry(entry: ArchiveEntry, action:() -> Unit) {
        if (okToAdd(entry.name)) {
            action()
        }
        seen.add(entry.name)
    }
}
