package com.beust.kobalt.plugin.java

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.CompilerActionInfo
import com.beust.kobalt.internal.ICompilerAction
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File

@Singleton
class JavaCompiler @Inject constructor(val jvmCompiler: JvmCompiler) {
    val compilerAction = object : ICompilerAction {
        override fun compile(info: CompilerActionInfo): TaskResult {

            val jvm = JavaInfo.create(File(SystemProperties.javaBase))
            val javac = jvm.javacExecutable

            val allArgs = arrayListOf(
                    javac!!.absolutePath,
                    "-d", info.outputDir)
            if (info.dependencies.size > 0) {
                allArgs.add("-classpath")
                allArgs.add(info.dependencies.map {it.jarFile.get()}.joinToString(File.pathSeparator))
            }
            allArgs.addAll(info.compilerArgs)
            allArgs.addAll(info.sourceFiles)

            val pb = ProcessBuilder(allArgs)
            pb.directory(File(info.outputDir))
            pb.inheritIO()
            val line = allArgs.joinToString(" ")
            log(1, "  Compiling ${info.sourceFiles.size} files with classpath size " + info.dependencies.size)
            log(2, "  Compiling $line")
            val process = pb.start()
            val errorCode = process.waitFor()

            return if (errorCode == 0) TaskResult(true, "Compilation succeeded")
            else TaskResult(false, "There were errors")
        }
    }

    /**
     * Create an ICompilerAction based on the parameters and send it to JvmCompiler.doCompile().
     */
    fun compile(project: Project?, context: KobaltContext?, dependencies: List<IClasspathDependency>,
            sourceFiles: List<String>, outputDir: String, args: List<String>) : TaskResult {

        val info = CompilerActionInfo(dependencies, sourceFiles, outputDir, args)
        return jvmCompiler.doCompile(project, context, compilerAction, info)
    }
}
