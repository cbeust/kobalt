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
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.log
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
        override fun compile(projectName: String?, info: CompilerActionInfo): TaskResult {
            if (info.sourceFiles.isEmpty()) {
                warn("No source files to compile")
                return TaskResult()
            }

            var command: String
            var errorMessage: String
            val compiler = ToolProvider.getSystemJavaCompiler()
            val result =
                if (compiler != null) {
                    log(2, "Found system Java compiler, using the compiler API")
                    val allArgs = arrayListOf(
                            "-d", KFiles.makeDir(info.directory!!, info.outputDir.path).path)
                    if (info.dependencies.size > 0) {
                        allArgs.add("-classpath")
                        allArgs.add(info.dependencies.map { it.jarFile.get() }.joinToString(File.pathSeparator))
                    }
                    allArgs.addAll(info.compilerArgs)

                    val fileManager = compiler.getStandardFileManager(null, null, null)
                    val fileObjects = fileManager.getJavaFileObjectsFromFiles(info.sourceFiles.map {
                            File(it)
                        }.filter {
                            it.isFile
                        })
                    val dc = DiagnosticCollector<JavaFileObject>()
                    val classes = arrayListOf<String>()
                    val writer = PrintWriter(System.out)
                    val task = compiler.getTask(writer, fileManager, dc, allArgs, classes, fileObjects)

                    command = "javac " + allArgs.joinToString(" ") + " " + info.sourceFiles.joinToString(" ")
                    log(2, "Launching\n$command")

                    log(1, "  Java compiling " + Strings.pluralizeAll("file", info.sourceFiles.size))
                    val result = task.call()
                    errorMessage = dc.diagnostics.joinToString("\n")
                    result
                } else {
                    log(2, "Didn't find system Java compiler, forking javac")
                    val allArgs = arrayListOf(
                            executable.absolutePath,
                            "-d", KFiles.makeDir(info.directory!!, info.outputDir.path).path)

                    if (info.dependencies.size > 0) {
                        allArgs.add("-classpath")
                        allArgs.add(info.dependencies.map { it.jarFile.get() }.joinToString(File.pathSeparator))
                    }

                    allArgs.addAll(info.compilerArgs)
                    allArgs.addAll(info.sourceFiles)

                    val pb = ProcessBuilder(allArgs)
                    pb.inheritIO()
                    val line = allArgs.joinToString(" ")
                    log(1, "  Java compiling " + Strings.pluralizeAll("file", info.sourceFiles.size))
                    log(2, "  Java compiling $line")

                    command = allArgs.joinToString(" ") + " " + info.sourceFiles.joinToString(" ")
                    val process = pb.start()
                    val errorCode = process.waitFor()
                    errorMessage = "Something went wrong running javac, need to switch to RunCommand"
                    errorCode == 0
                }

            return if (result) {
                    TaskResult(true, "Compilation succeeded")
                } else {
                    val message = "Compilation errors, command:\n$command" + errorMessage
                    log(1, message)
                    TaskResult(false, message)
                }

        }
    }

    /**
     * Invoke the given executable on the CompilerActionInfo.
     */
    private fun run(project: Project?, context: KobaltContext?, cai: CompilerActionInfo, executable: File): TaskResult {
        return jvmCompiler.doCompile(project, context, compilerAction(executable), cai)
    }

    fun compile(project: Project?, context: KobaltContext?, cai: CompilerActionInfo) : TaskResult
        = run(project, context, cai, JavaInfo.create(File(SystemProperties.javaBase)).javacExecutable!!)

    fun javadoc(project: Project?, context: KobaltContext?, cai: CompilerActionInfo) : TaskResult
        = run(project, context, cai, JavaInfo.create(File(SystemProperties.javaBase)).javadocExecutable!!)
}
