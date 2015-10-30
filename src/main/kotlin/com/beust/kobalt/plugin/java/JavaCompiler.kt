package com.beust.kobalt.plugin.java

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File

@Singleton
class JavaCompiler @Inject constructor(val dependencyManager: DependencyManager){
    fun compile(name: String, directory: String?, compilerArgs: List<String>, cpList: List<IClasspathDependency>,
            sourceFiles: List<String>, outputDirectory: File): TaskResult {

        outputDirectory.mkdirs()
        val jvm = JavaInfo.create(File(SystemProperties.javaBase))
        val javac = jvm.javacExecutable

        val args = arrayListOf(
                javac!!.absolutePath,
                "-d", outputDirectory.absolutePath)
        if (cpList.size > 0) {
            val fullClasspath = dependencyManager.transitiveClosure(cpList)
            val stringClasspath = fullClasspath.map { it.jarFile.get().absolutePath }
            JvmCompilerPlugin.validateClasspath(stringClasspath)
            args.add("-classpath")
            args.add(stringClasspath.joinToString(File.pathSeparator))
        }
        args.addAll(compilerArgs)
        args.addAll(sourceFiles)

        val pb = ProcessBuilder(args)
        if (directory != null) {
            pb.directory(File(directory))
        }
        pb.inheritIO()
        //        pb.redirectErrorStream(true)
        //        pb.redirectError(File("/tmp/kobalt-err"))
        //        pb.redirectOutput(File("/tmp/kobalt-out"))
        val line = args.joinToString(" ")
        log(1, "  Compiling ${sourceFiles.size} files")
        log(2, "  Compiling $name:\n$line")
        val process = pb.start()
        val errorCode = process.waitFor()

        return if (errorCode == 0) TaskResult(true, "Compilation succeeded")
        else TaskResult(false, "There were errors")

    }
}
