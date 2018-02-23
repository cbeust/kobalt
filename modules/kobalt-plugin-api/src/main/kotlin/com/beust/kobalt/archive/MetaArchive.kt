package com.beust.kobalt.archive

import com.beust.kobalt.Glob
import com.beust.kobalt.misc.KFiles
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.jar.Manifest
import org.apache.commons.compress.archivers.zip.ZipFile as ApacheZipFile

/**
 * Abstraction of a zip/jar/war archive that automatically manages the addition of expanded jar files.
 * Uses ZipArchiveOutputStream for fast inclusion of expanded jar files.
 */
class MetaArchive(outputFile: File, val manifest: Manifest?) : Closeable {
    companion object {
        const val MANIFEST_MF = "META-INF/MANIFEST.MF"
    }

    private val zos= ZipArchiveOutputStream(outputFile).apply {
        encoding = "UTF-8"
    }

    init {
        // If no manifest was passed, create an empty one so it's the first one in the archive
        val m = manifest ?: Manifest()
        val manifestFile = File.createTempFile("kobalt", "tmpManifest")
        addEntry(ZipArchiveEntry("META-INF/"), null)
        if (manifest != null) {
            FileOutputStream(manifestFile).use { fos ->
                m.write(fos)
            }
        }
        val entry = zos.createArchiveEntry(manifestFile, MetaArchive.MANIFEST_MF)
        addEntry(entry, FileInputStream(manifestFile))
    }


    fun addFile(f: File, entryFile: File, path: String?) {
        maybeCreateParentDirectories(f)
        addFile2(f, entryFile, path)
    }

    private fun addFile2(f: File, entryFile: File, path: String?) {
        val file = f.normalize()
        FileInputStream(file).use { inputStream ->
            val actualPath = KFiles.fixSlashes(if (path != null) path + entryFile.path else entryFile.path)
            ZipArchiveEntry(actualPath).let { entry ->
                maybeCreateParentDirectories(File(actualPath))
                maybeAddEntry(entry) {
                    addEntry(entry, inputStream)
                }
            }
        }
    }

    private val createdDirs = hashSetOf<String>()

    /**
     * For an entry a/b/c/File, an entry needs to be created for each individual directory:
     * a/
     * a/b/
     * a/b/c
     * a/b/c/File
     */
    private fun maybeCreateParentDirectories(file: File) {
        val toCreate = arrayListOf<String>()
        var current = file.parentFile
        while (current != null && current.path != ".") {
            if (!createdDirs.contains(current.path)) {
                toCreate.add(0, KFiles.fixSlashes(current) + "/")
                createdDirs.add(current.path)
            }
            current = current.parentFile
        }
        toCreate.forEach { dir ->
            addEntry(ZipArchiveEntry(dir), null)
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

    @Suppress("PrivatePropertyName")
    private val DEFAULT_JAR_EXCLUDES =
            Glob("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", MANIFEST_MF)

    private val seen = hashSetOf<String>()

    private fun okToAdd(name: String): Boolean = ! seen.contains(name)
            && ! KFiles.isExcluded(name, DEFAULT_JAR_EXCLUDES)

    override fun close() = zos.close()

    private fun addEntry(entry: ArchiveEntry, inputStream: FileInputStream?) {
        zos.putArchiveEntry(entry)
        inputStream?.use { ins ->
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
