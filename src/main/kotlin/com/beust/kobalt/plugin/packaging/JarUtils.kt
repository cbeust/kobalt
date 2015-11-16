package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.IFileSpec
import com.beust.kobalt.misc.log
import com.google.common.io.CharStreams
import java.io.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

public class JarUtils {
    companion object {
        val DEFAULT_HANDLER: (Exception) -> Unit = { ex: Exception ->
            // Ignore duplicate entry exceptions
            if (! ex.message?.contains("duplicate")!!) {
                throw ex
            }
        }

        public fun addFiles(directory: String, files: List<IncludedFile>, target: ZipOutputStream,
                expandJarFiles: Boolean,
                onError: (Exception) -> Unit = DEFAULT_HANDLER) {
            files.forEach {
                addSingleFile(directory, it, target, expandJarFiles, onError)
            }
        }

        public fun addSingleFile(directory: String, file: IncludedFile, outputStream: ZipOutputStream,
                expandJarFiles: Boolean, onError: (Exception) -> Unit = DEFAULT_HANDLER) {
            file.specs.forEach { spec ->
                val path = spec.toString()
                spec.toFiles(directory + "/" + file.from).forEach { source ->
                    if (source.isDirectory) {
                        log(2, "Writing contents of directory ${source}")

                        // Directory
                        var name = path
                        if (!name.isEmpty()) {
                            if (!name.endsWith("/")) name += "/"
                            val entry = JarEntry(name)
                            entry.time = source.lastModified()
                            try {
                                outputStream.putNextEntry(entry)
                            } finally {
                                outputStream.closeEntry()
                            }
                        }
                        val includedFile = IncludedFile(From(source.path), To(""), listOf(IFileSpec.Glob("**")))
                        addSingleFile(directory, includedFile, outputStream, expandJarFiles)
                    } else {
                        if (expandJarFiles and source.name.endsWith(".jar")) {
                            log(2, "Writing contents of jar file ${source}")
                            val stream = JarInputStream(FileInputStream(source))
                            var entry = stream.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    val ins = JarFile(source).getInputStream(entry)
                                    addEntry(ins, JarEntry(entry), outputStream, onError)
                                }
                                entry = stream.nextEntry
                            }
                        } else {
                            val entry = JarEntry((file.to + source.path).replace("\\", "/"))
                            entry.time = source.lastModified()
                            val fromPath = (file.from + "/" + source.path).replace("\\", "/")
                            val entryFile = File(directory, fromPath)
                            if (! entryFile.exists()) {
                                throw AssertionError("File should exist: ${entryFile}")
                            }
                            addEntry(FileInputStream(entryFile), entry, outputStream, onError)
                        }
                    }
                }
            }
        }

        private fun addEntry(inputStream: InputStream, entry: ZipEntry, outputStream: ZipOutputStream,
                onError: (Exception) -> Unit = DEFAULT_HANDLER) {
            var bis: BufferedInputStream? = null
            try {
                outputStream.putNextEntry(entry)
                bis = BufferedInputStream(inputStream)

                val buffer = ByteArray(50 * 1024)
                while (true) {
                    val count = bis.read(buffer)
                    if (count == -1) break
                    outputStream.write(buffer, 0, count)
                }
                outputStream.closeEntry()
            } catch(ex: Exception) {
                onError(ex)
            } finally {
                bis?.close()
            }
        }

        fun extractTextFile(zip : ZipFile, fileName: String) : String? {
            val enumEntries = zip.entries()
            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                if (file.name == fileName) {
                    log(2, "Found $fileName in $zip")
                    zip.getInputStream(file).use { ins ->
                        return CharStreams.toString(InputStreamReader(ins, "UTF-8"))
                    }
                }
            }
            return null
        }

        fun extractJarFile(jarFile: File, destDir: File) {
            val jar = java.util.jar.JarFile(jarFile)
            val enumEntries = jar.entries()
            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                val f = File(destDir.path + java.io.File.separator + file.name)
                if (file.isDirectory) {
                    f.mkdir()
                    continue
                }

                jar.getInputStream(file).use { ins ->
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
