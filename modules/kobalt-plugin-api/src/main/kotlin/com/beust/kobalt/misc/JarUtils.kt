package com.beust.kobalt.misc

import com.beust.kobalt.From
import com.beust.kobalt.IFileSpec
import com.beust.kobalt.IncludedFile
import com.beust.kobalt.To
import com.beust.kobalt.archive.MetaArchive
import com.google.common.io.CharStreams
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.jar.JarFile
import java.util.zip.ZipFile

class JarUtils {
    companion object {
        val DEFAULT_HANDLER: (Exception) -> Unit = { ex: Exception ->
            // Ignore duplicate entry exceptions
            if (! ex.message?.contains("duplicate")!!) {
                throw ex
            }
        }

        fun addFiles(directory: String, files: List<IncludedFile>, metaArchive: MetaArchive,
                expandJarFiles: Boolean,
                onError: (Exception) -> Unit = DEFAULT_HANDLER) {
            files.forEach {
                addSingleFile(directory, it, metaArchive, expandJarFiles, onError)
            }
        }

        fun addSingleFile(directory: String, file: IncludedFile, metaArchive: MetaArchive,
                expandJarFiles: Boolean, onError: (Exception) -> Unit = DEFAULT_HANDLER) {
            val foundFiles = file.allFromFiles(directory)
            foundFiles.forEach { foundFile ->

                // Turn the found file into the local physical file that will be put in the jar file
                val fromFile = file.from(foundFile.path)
                val localFile = if (fromFile.isAbsolute) fromFile
                    else File(directory, fromFile.path)

                if (!localFile.exists()) {
                    throw AssertionError("File should exist: $localFile")
                }

                if (foundFile.isDirectory) {
                    kobaltLog(2, "  Writing contents of directory $foundFile")

                    // Directory
                    val includedFile = IncludedFile(From(""), To(""), listOf(IFileSpec.GlobSpec("**")))
                    addSingleFile(localFile.path, includedFile, metaArchive, expandJarFiles)
                } else {
                    try {
                        if (file.expandJarFiles && foundFile.name.endsWith(".jar") && !file.from.contains("resources")) {
                            kobaltLog(2, "  Writing contents of jar file $foundFile")
                            metaArchive.addArchive(foundFile)
                        } else {
                            metaArchive.addFile(File(directory, fromFile.path), foundFile.path)
                        }
                    } catch(ex: Exception) {
                        onError(ex)
                    }
                }
            }
        }

        fun extractTextFile(zip : ZipFile, fileName: String) : String? {
            val enumEntries = zip.entries()
            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                if (file.name == fileName) {
                    kobaltLog(2, "Found $fileName in ${zip.name}")
                    zip.getInputStream(file).use { ins ->
                        return CharStreams.toString(InputStreamReader(ins, "UTF-8"))
                    }
                }
            }
            return null
        }

        fun extractJarFile(file: File, destDir: File) = extractZipFile(JarFile(file), destDir)

        fun extractZipFile(zipFile: ZipFile, destDir: File) {
            val enumEntries = zipFile.entries()
            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                val f = File(destDir.path + File.separator + file.name)
                if (file.isDirectory) {
                    f.mkdir()
                    continue
                }

                zipFile.getInputStream(file).use { ins ->
                    f.parentFile.mkdirs()
                    FileOutputStream(f).use { fos ->
                        while (ins.available() > 0) {
                            fos.write(ins.read())
                        }
                    }
                }
            }
        }
    }
}

