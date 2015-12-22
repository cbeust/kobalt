package com.beust.kobalt.misc

import com.beust.kobalt.IFileSpec
import com.google.common.io.CharStreams
import java.io.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
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

        private val DEFAULT_JAR_EXCLUDES = arrayListOf(
                IFileSpec.Glob("META-INF/*.SF"),
                IFileSpec.Glob("META-INF/*.DSA"),
                IFileSpec.Glob("META-INF/*.RSA"))

        public fun addSingleFile(directory: String, file: IncludedFile, outputStream: ZipOutputStream,
                expandJarFiles: Boolean, onError: (Exception) -> Unit = DEFAULT_HANDLER) {
            val allFiles = file.allFromFiles(directory)
            allFiles.forEach { relSource ->
                val source =
                        if (relSource.isAbsolute) relSource
                        else File(KFiles.joinDir(directory, file.from, relSource.path))
                if (source.isDirectory) {
                    log(2, "Writing contents of directory $source")

                    // Directory
                    var name = source.name
                    if (!name.isEmpty()) {
                        if (!name.endsWith("/")) name += "/"
                        val entry = JarEntry(name)
                        entry.time = source.lastModified()
                        try {
                            outputStream.putNextEntry(entry)
                        } catch(ex: ZipException) {
                            log(2, "Can't add $name: ${ex.message}")
                        } finally {
                            outputStream.closeEntry()
                        }
                    }
                    val includedFile = IncludedFile(From(source.path), To(""), listOf(IFileSpec.Glob("**")))
                    addSingleFile(".", includedFile, outputStream, expandJarFiles)
                } else {
                    if (expandJarFiles and source.name.endsWith(".jar")) {
                        log(2, "Writing contents of jar file $source")
                        val stream = JarInputStream(FileInputStream(source))
                        var entry = stream.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && !KFiles.isExcluded(entry.name, DEFAULT_JAR_EXCLUDES)) {
                                val ins = JarFile(source).getInputStream(entry)
                                addEntry(ins, JarEntry(entry), outputStream, onError)
                            }
                            entry = stream.nextEntry
                        }
                    } else {
                        val entry = JarEntry(file.to + relSource.path.replace("\\", "/"))
                        entry.time = source.lastModified()
                        val entryFile = source
                        if (!entryFile.exists()) {
                            throw AssertionError("File should exist: $entryFile")
                        }
                        addEntry(FileInputStream(entryFile), entry, outputStream, onError)
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
                    log(2, "Found $fileName in ${zip.name}")
                    zip.getInputStream(file).use { ins ->
                        return CharStreams.toString(InputStreamReader(ins, "UTF-8"))
                    }
                }
            }
            return null
        }

        fun extractJarFile(jarFile: File, destDir: File) {
            val jar = JarFile(jarFile)
            val enumEntries = jar.entries()
            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                val f = File(destDir.path + File.separator + file.name)
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

open class Direction(open val p: String) {
    override public fun toString() = path
    public val path: String get() = if (p.isEmpty() or p.endsWith("/")) p else p + "/"
}

class IncludedFile(val fromOriginal: From, val toOriginal: To, val specs: List<IFileSpec>) {
    constructor(specs: List<IFileSpec>) : this(From(""), To(""), specs)
    public val from: String get() = fromOriginal.path.replace("\\", "/")
    public val to: String get() = toOriginal.path.replace("\\", "/")
    override public fun toString() = toString("IncludedFile",
            "files", specs.map { it.toString() }.joinToString(", "),
            "from", from,
            "to", to)

    fun allFromFiles(directory: String? = null): List<File> {
        val result = arrayListOf<File>()
        specs.forEach { spec ->
            val fullDir = if (directory == null) from else KFiles.joinDir(directory, from)
            spec.toFiles(fullDir).forEach { source ->
                result.add(if (source.isAbsolute) source else File(source.path))
            }
        }
        return result
    }
}

class From(override val p: String) : Direction(p)

class To(override val p: String) : Direction(p)
