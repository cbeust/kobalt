package com.beust.kobalt.misc

import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.homeDir
import com.beust.kobalt.kotlin.BuildFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class KFiles {
    val kobaltJar : String
        get() {
            val envJar = System.getenv("KOBALT_JAR")
            if (envJar != null) {
                debug("Using kobalt jar $envJar")
                return File(envJar).absolutePath
            } else {
                val jar = joinDir(distributionsDir, Kobalt.version, "kobalt/wrapper/kobalt-" + Kobalt.version + ".jar")
                val jarFile = File(jar)
                if (jarFile.exists()) {
                    return jarFile.absolutePath
                } else {
                    // Will only happen when building kobalt itself: the jar file might not be in the dist/ directory
                    // yet since we're currently building it. Instead, use the classes directly
                    debug("Couldn't find ${jarFile.absolutePath}, using build/classes/main")
                    return java.io.File(joinDir("build", "classes", "main")).absolutePath
                }
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
        val localRepo = homeDir(KOBALT_DOT_DIR, "repository")

        /** Where all the .zip files are extracted */
        val distributionsDir = homeDir(KOBALT_DOT_DIR, "wrapper", "dist")

        // Directories under ./.kobalt
        val SCRIPT_BUILD_DIR : String = "build"
        val CLASSES_DIR : String = "classes"

        /** Where build file and support source files go, under KOBALT_DIR */
        private val SRC = "src"

        val TEST_CLASSES_DIR : String = "test-classes"

        /**
         * Join the paths elements with the file separator.
         */
        fun joinDir(paths: List<String>): String = paths.joinToString(File.separator)

        /**
         * Join the paths elements with the file separator.
         */
        fun joinDir(vararg ts: String): String = ts.toArrayList().joinToString(File.separator)

        /**
         * The paths elements are expected to be a directory. Make that directory and join the
         * elements with the file separator.
         */
        fun joinAndMakeDir(paths: List<String>) = joinDir(paths).apply { File(this).mkdirs() }

        /**
         * The paths elements are expected to be a directory. Make that directory and join the
         * elements with the file separator.
         */
        fun joinAndMakeDir(vararg ts: String) = joinAndMakeDir(ts.toList())

        /**
         * The paths elements are expected to be a file. Make that parent directory of that file and join the
         * elements with the file separator.
         */
        fun joinFileAndMakeDir(vararg ts: String) = joinDir(joinAndMakeDir(ts.slice(0..ts.size - 2)), ts[ts.size - 1])

        fun makeDir(dir: String, s: String? = null) =
                (if (s != null) File(dir, s) else File(dir)).apply { mkdirs() }

        fun findRecursively(rootDir: File) : List<String> =
                findRecursively(rootDir, arrayListOf(), { s -> true })

        fun findRecursively(rootDir: File, directories: List<File>,
                function: Function1<String, Boolean>): List<String> {
            var result = arrayListOf<String>()

            val allDirs = arrayListOf<File>()
            if (directories.isEmpty()) {
                allDirs.add(rootDir)
            } else {
                allDirs.addAll(directories.map { File(rootDir, it.path) })
            }

            allDirs.forEach {
                if (! it.exists()) {
                    log(2, "Couldn't find directory $it")
                } else {
                    result.addAll(findRecursively(it, function))
                }
            }
            // Return files relative to rootDir
            val r = result.map { it.substring(rootDir.absolutePath.length + 1)}
            return r
        }

        fun findRecursively(directory: File, function: Function1<String, Boolean>): List<String> {
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
                throw IllegalArgumentException("Couldn't locate $KOBALT_DOT_DIR in $startDir")
            }
            return File(result, KOBALT_DOT_DIR)
        }

        /**
         * The build location for build scripts is .kobalt/build
         */
        fun findBuildScriptLocation(buildFile: BuildFile, jarFile: String) : String {
            val result = joinDir(findDotDir(buildFile.directory).absolutePath, KFiles.SCRIPT_BUILD_DIR, jarFile)
            log(2, "Script jar file: $result")
            return result
        }

        fun saveFile(file: File, text: String) {
            file.absoluteFile.parentFile.mkdirs()
            file.writeText(text)
            log(2, "Wrote $file")
        }

        private fun isWindows() = System.getProperty("os.name").contains("Windows");

        fun copy(from: Path?, to: Path?, option: StandardCopyOption) {
            if (isWindows() && to!!.toFile().exists()) {
                log(2, "Windows detected, not overwriting ${to!!}")
            } else {
                try {
                    log(2, "Copy from $from!! to ${to!!}")
                    Files.copy(from, to, option)
                } catch(ex: IOException) {
                    // Windows is anal about this
                    log(1, "Couldn't copy $from to $to: ${ex.message}")
                }
            }
        }

        fun createTempFile(suffix : String = "", deleteOnExit: Boolean = false) : File =
            File.createTempFile("kobalt", suffix, File(SystemProperties.tmpDir)).let {
                if (deleteOnExit) it.deleteOnExit()
                return it
            }

        fun src(filePath: String): String = KFiles.joinDir(KOBALT_DIR, SRC, filePath)
    }

    fun findRecursively(directory: File, function: Function1<String, Boolean>): List<String> {
        return KFiles.findRecursively(directory, function)
    }

    fun findRecursively(rootDir: File, directories: List<File>,
            function: Function1<String, Boolean>): List<String> {
        return KFiles.findRecursively(rootDir, directories, function)
    }
}
