package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.IFileSpec
import com.beust.kobalt.misc.KobaltLogger
import java.io.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class JarUtils : KobaltLogger {
    companion object {
//        private fun isExcluded(entryName: String) : Boolean {
//            val isAuth = entryName.startsWith("META-INF") and (
//                    entryName.endsWith(".SF") or entryName.endsWith(".DSA") or entryName.endsWith("RSA"))
//            val isSource = entryName.endsWith(".java")
//            return isAuth or isSource
//        }

        /**
         * Add the content of a jar file to another jar file.
         */
//        private fun addJarFile(inFile: File, outStream: ZipOutputStream, seen: HashSet<String>) {
//            val inJarFile = JarFile(inFile)
//            val stream = JarInputStream(FileInputStream(inFile))
//            var entry = stream.getNextEntry()
//            // Quick and dirty benchmarks to assess the impact of the byte array size (milliseconds):
//            // 10000: 7883 7873
//            // 50000: 7947
//            // 20000: 7858 7737 7730
//            // 10000: 7939 7924
//            // Probably need to do this more formally but it doesn't seem to matter that much
//            val buf = ByteArray(20000)
//
//            while (entry != null) {
//                if (! entry.isDirectory()) {
//                    val entryName = entry.getName()
//                    if (! seen.contains(entryName) && ! isExcluded(entryName)) {
//                        seen.add(entryName)
//                        outStream.putNextEntry(entry)
//                        val zis = inJarFile.getInputStream(entry)
//
//                        var len = zis.read(buf)
//                        while (len >= 0) {
//                            outStream.write(buf, 0, len);
//                            len = zis.read(buf)
//                        }
//                    }
//                }
//                entry = stream.getNextJarEntry()
//            }
//
//            stream.close()
//        }

        val defaultHandler: (Exception) -> Unit = { ex: Exception ->
            // Ignore duplicate entry exceptions
            if (! ex.getMessage()?.contains("duplicate")!!) {
                throw ex
            }
        }

        public fun addFiles(directory: String, files: List<IncludedFile>, target: ZipOutputStream,
                expandJarFiles: Boolean,
                onError: (Exception) -> Unit = defaultHandler) {
            files.forEach {
                addSingleFile(directory, it, target, expandJarFiles, onError)
            }
        }

        public fun addSingleFile(directory: String, file: IncludedFile, outputStream: ZipOutputStream,
                expandJarFiles: Boolean, onError: (Exception) -> Unit = defaultHandler) {
            file.specs.forEach { spec ->
                val path = spec.toString()
                spec.toFiles(directory + "/" + file.from).forEach { source ->
                    if (source.isDirectory) {
                        // Directory
                        var name = path
                        if (!name.isEmpty()) {
                            if (!name.endsWith("/")) name += "/"
                            val entry = JarEntry(name)
                            entry.time = source.lastModified()
                            outputStream.putNextEntry(entry)
                            outputStream.closeEntry()
                        }
                        val fileSpecs: List<IFileSpec> = source.listFiles().map { IFileSpec.FileSpec(it.name) }
                        val subFiles = IncludedFile(From(file.from), To(file.to), fileSpecs)
                        addSingleFile(directory, subFiles, outputStream, expandJarFiles)
                    } else {
                        if (expandJarFiles and source.name.endsWith(".jar")) {
                            KobaltLogger.log(2, "Writing contents of jar file ${source}")
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
                onError: (Exception) -> Unit = defaultHandler) {
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

        fun removeDuplicateEntries(fromJarFile: File, toFile: File) {
            val fromFile = JarFile(fromJarFile)
            var entries = fromFile.entries()
            val os = JarOutputStream(FileOutputStream(toFile))
            val seen = hashSetOf<String>()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (! seen.contains(entry.name)) {
                    val ins = fromFile.getInputStream(entry)
                    addEntry(ins, JarEntry(entry), os)
                }
                seen.add(entry.name)
            }
            os.close()

            KobaltLogger.log(1, "Deduplicated $fromFile.name")
        }

    }
}
