package com.beust.kobalt.plugin.java

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.CompilerActionInfo
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.ICompilerAction
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.warn
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.io.PrintWriter
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

@Singleton
class JavaCompiler @Inject constructor(val jvmCompiler: JvmCompiler) {
    fun compilerAction(executable: File) = object : ICompilerAction {
        override fun compile(info: CompilerActionInfo): TaskResult {
            if (info.sourceFiles.isEmpty()) {
                warn("No source files to compile")
                return TaskResult()
            }

            val allArgs = arrayListOf(
                    "-d", KFiles.makeDir(info.directory!!, info.outputDir.path).path)
            if (info.dependencies.size > 0) {
                allArgs.add("-classpath")
                allArgs.add(info.dependencies.map { it.jarFile.get() }.joinToString(File.pathSeparator))
            }
            allArgs.addAll(info.compilerArgs)

            val compiler = ToolProvider.getSystemJavaCompiler()
            val fileManager = compiler.getStandardFileManager(null, null, null)
            val fileObjects = fileManager.getJavaFileObjectsFromFiles(info.sourceFiles.map { File(it) })
            val dc = DiagnosticCollector<JavaFileObject>()
            val classes = arrayListOf<String>()
            val task = compiler.getTask(PrintWriter(System.out), fileManager, dc, allArgs, classes, fileObjects)
            val result = task.call()

            return if (result) TaskResult(true, "Compilation succeeded")
                else TaskResult(false, "There were errors")

        }
    }

    /**
     * Invoke the given executale on the CompilerActionInfo.
     */
    private fun run(project: Project?, context: KobaltContext?, cai: CompilerActionInfo, executable: File): TaskResult {
        return jvmCompiler.doCompile(project, context, compilerAction(executable), cai)
    }

    fun compile(project: Project?, context: KobaltContext?, cai: CompilerActionInfo) : TaskResult
        = run(project, context, cai, JavaInfo.create(File(SystemProperties.javaBase)).javacExecutable!!)

    fun javadoc(project: Project?, context: KobaltContext?, cai: CompilerActionInfo) : TaskResult
        = run(project, context, cai, JavaInfo.create(File(SystemProperties.javaBase)).javadocExecutable!!)
}
