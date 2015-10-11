package com.beust.kobalt.internal

import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import java.io.File

abstract class GenericTestRunner(open val project: Project, open val classpath: List<IClasspathDependency>)
        : KobaltLogger {
    abstract val mainClass: String
    abstract val args: List<String>

    protected fun findTestClasses(): List<String> {
        val path = KFiles.joinDir(project.directory, project.buildDirectory!!, KFiles.TEST_CLASSES_DIR)
        val result = KFiles.findRecursively(File(path),
                arrayListOf(File("."))) { file -> file.endsWith("Test.class")
        }.map {
            it.replace("/", ".").replace(".class", "").substring(2)
        }
        return result
    }

    fun runTests() {
        val jvm = JavaInfo.create(File(SystemProperties.javaBase))
        val java = jvm.javaExecutable
        val allArgs = arrayListOf<String>()
        allArgs.add(java!!.getAbsolutePath())
        allArgs.add("-classpath")
        allArgs.add(classpath.map { it.jarFile.get().getAbsolutePath() }.join(File.pathSeparator))
        allArgs.add(mainClass)
        allArgs.addAll(args)

        val pb = ProcessBuilder(allArgs)
        pb.directory(File(project.directory))
        pb.inheritIO()
        log(1, "Running tests with classpath size ${classpath.size()}")
        val process = pb.start()
        val errorCode = process.waitFor()
        if (errorCode == 0) {
            log(1, "All tests passed")
        } else {
            log(1, "Test failures")
        }

    }
}

