package com.beust.kobalt.internal

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.net.URLClassLoader

abstract class GenericTestRunner(open val project: Project, open val classpath: List<IClasspathDependency>) {
    abstract val mainClass: String
    abstract val args: List<String>

    protected fun findTestClasses(): List<String> {
        val path = KFiles.joinDir(project.directory, project.buildDirectory!!, KFiles.TEST_CLASSES_DIR)
        val result = KFiles.findRecursively(File(path), arrayListOf(File(".")), {
            file -> file.endsWith(".class")
        }).map {
            it.replace("/", ".").replace("\\", ".").replace(".class", "").substring(2)
        }.filter {
            try {
                // Only keep classes with a parameterless constructor
                val urls = arrayOf(File(path).toURI().toURL()) +
                        classpath.map { it.jarFile.get().toURI().toURL() }
                URLClassLoader(urls).loadClass(it).getConstructor()
                true
            } catch(ex: Exception) {
                log(2, "Skipping non test class $it: ${ex.message}")
                false
            }
        }

        log(2, "Found ${result.size} test classes")
        return result
    }

    /**
     * @return true if all the tests passed
     */
    fun runTests() : Boolean {
        val jvm = JavaInfo.create(File(SystemProperties.javaBase))
        val java = jvm.javaExecutable
        if (args.size > 0) {
            val allArgs = arrayListOf<String>().apply {
                add(java!!.absolutePath)
                add("-classpath")
                add(classpath.map { it.jarFile.get().absolutePath }.joinToString(File.pathSeparator))
                add(mainClass)
                addAll(args)
            }

            val pb = ProcessBuilder(allArgs)
            pb.directory(File(project.directory))
            pb.inheritIO()
            log(1, "Running tests with classpath size ${classpath.size}")
            log(2, "Launching " + allArgs.joinToString(" "))
            val process = pb.start()
            val errorCode = process.waitFor()
            if (errorCode == 0) {
                log(1, "All tests passed")
            } else {
                log(1, "Test failures")
            }
            return errorCode == 0
        } else {
            log(2, "Couldn't find any test classes")
            return true
        }
    }
}

