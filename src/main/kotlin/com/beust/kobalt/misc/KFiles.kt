package com.beust.kobalt.misc

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.homeDir
import com.beust.kobalt.kotlin.BuildFile
import com.beust.kobalt.maven.KobaltException
import com.beust.kobalt.SystemProperties
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public class KFiles {
    val kobaltJar : String
        get() {
            val jar = joinDir(distributionsDir, Kobalt.version, "kobalt/wrapper/kobalt-" + Kobalt.version + ".jar")
            val jarFile = File(jar)
            val envJar = System.getenv("KOBALT_JAR")
            if (! jarFile.exists() && envJar != null) {
                debug("Using kobalt jar $envJar")
                return File(envJar).absolutePath
            }
            if (! jarFile.exists()) {
                // Will only happen when building kobalt itself: the jar file might not be in the dist/ directory
                // yet since we're currently building it. Instead, use the classes directly
                return File(joinDir("build", "classes", "main")).absolutePath
            } else {
                return jar
            }
        }

    init {
        File(KOBALT_DOT_DIR).mkdirs()
    }

    companion object {
        private const val KOBALT_DOT_DIR : String = ".kobalt"
        const val KOBALT_DIR : String = "kobalt"
        const val KOBALT_BUILD_DIR = "kobaltBuild"

        // Directories under ~/.kobalt
        public val localRepo = homeDir(KOBALT_DOT_DIR, "repository")

        /** Where all the .zip files are extracted */
        public val distributionsDir = homeDir(KOBALT_DOT_DIR, "wrapper", "dist")

        // Directories under ./.kobalt
        public val SCRIPT_BUILD_DIR : String = "build"
        public val CLASSES_DIR : String = "classes"

        /** Where build file and support source files go, under KOBALT_DIR */
        private val SRC = "src"

        public val TEST_CLASSES_DIR : String = "test-classes"

        public fun joinDir(vararg ts: String): String = ts.toArrayList().join(File.separator)

        public fun makeDir(dir: String, s: String) : File {
            val result = File(dir, s)
            result.mkdirs()
            return result
        }

        public fun findRecursively(rootDir: File) : List<String> =
                findRecursively(rootDir, arrayListOf(), { s -> true })

        public fun findRecursively(rootDir: File, directories: List<File>,
                function: Function1<String, Boolean>): List<String> {
            var result = arrayListOf<String>()

            val allDirs = arrayListOf<File>()
            if (directories.isEmpty()) {
                allDirs.add(rootDir)
            } else {
                allDirs.addAll(directories.map { File(rootDir, it.getPath()) })
            }

            allDirs.forEach {
                if (! it.exists()) {
                    log(2, "Couldn't find directory ${it}")
                } else {
                    result.addAll(findRecursively(it, function))
                }
            }
            // Return files relative to rootDir
            val r = result.map { it.substring(rootDir.getAbsolutePath().length() + 1)}
            return r
        }

        public fun findRecursively(directory: File, function: Function1<String, Boolean>): List<String> {
            var result = arrayListOf<String>()
            directory.listFiles().forEach {
                if (it.isFile && function(it.absolutePath)) {
                    result.add(it.absolutePath)
                } else if (it.isDirectory) {
                    result.addAll(findRecursively(it, function))
                }
            }
            return result
        }

        fun copyRecursively(from: File, to: File) {
            // Need to wait until copyRecursively supports an overwrite: Boolean = false parameter
            // Until then, wipe everything first
            to.deleteRecursively()
            to.mkdirs()
            from.copyRecursively(to)
        }

        fun findDotDir(startDir: File) : File {
            var result = startDir
            while (result != null && ! File(result, KOBALT_DOT_DIR).exists()) {
                result = result.parentFile
            }
            if (result == null) {
                throw IllegalArgumentException("Couldn't locate ${KOBALT_DOT_DIR} in ${startDir}")
            }
            return File(result, KOBALT_DOT_DIR)
        }

        /**
         * The build location for build scripts is .kobalt/build
         */
        fun findBuildScriptLocation(buildFile: BuildFile, jarFile: String) : String {
            val result = joinDir(findDotDir(buildFile.directory).absolutePath, KFiles.SCRIPT_BUILD_DIR, jarFile)
            log(2, "Script jar file: ${result}")
            return result
        }

        public fun saveFile(file: File, text: String) {
            file.absoluteFile.parentFile.mkdirs()
            file.writeText(text)
            log(2, "Wrote ${file}")
        }

        private fun isWindows() = System.getProperty("os.name").contains("Windows");

        public fun copy(from: Path?, to: Path?, option: StandardCopyOption) {
            if (isWindows() && to!!.toFile().exists()) {
                log(2, "Windows detected, not overwriting ${to!!}")
            } else {
                try {
                    log(2, "Copy from $from!! to ${to!!}")
                    Files.copy(from, to, option)
                } catch(ex: IOException) {
                    // Windows is anal about this
                    log(1, "Couldn't copy ${from} to ${to}: ${ex.getMessage()}")
                }
            }
        }

        public fun copy(from: InputStream, to: OutputStream, bufSize: Int): Long {
            val buf = ByteArray(bufSize)
            var total: Long = 0
            while (true) {
                val r = from.read(buf, 0, buf.size())
                if (r == -1) {
                    break
                }
                to.write(buf, 0, r)
                total += r.toLong()
            }
            return total
        }

        public fun createTempFile(suffix : String = "", deleteOnExit: Boolean = false) : File =
            File.createTempFile("kobalt", suffix, File(SystemProperties.tmpDir)).let {
                if (deleteOnExit) it.deleteOnExit()
                return it
            }

        fun src(filePath: String): String = KFiles.joinDir(KOBALT_DIR, SRC, filePath)
    }

    public fun findRecursively(directory: File, function: Function1<String, Boolean>): List<String> {
        return KFiles.findRecursively(directory, function)
    }

    public fun findRecursively(rootDir: File, directories: List<File>,
            function: Function1<String, Boolean>): List<String> {
        return KFiles.findRecursively(rootDir, directories, function)
    }

    public fun saveFile(file: File, bytes: ByteArray) {
        file.parentFile.mkdirs()
        val os = file.outputStream()
        try {
            os.write(bytes)
        } finally {
            os.close()
        }
    }

}
