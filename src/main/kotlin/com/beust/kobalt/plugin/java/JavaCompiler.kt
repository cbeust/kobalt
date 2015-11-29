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
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.android.forward
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File

@Singleton
class JavaCompiler @Inject constructor(val jvmCompiler: JvmCompiler) {
    fun compilerAction(executable: File) = object : ICompilerAction {
        override fun compile(info: CompilerActionInfo): TaskResult {
            info.outputDir.mkdirs()

            val allArgs = arrayListOf(
                    executable.absolutePath,
                    "-d", KFiles.makeDir(info.directory!!, info.outputDir.path).path)
            if (info.dependencies.size > 0) {
                allArgs.add("-classpath")
                allArgs.add(info.dependencies.map {it.jarFile.get()}.joinToString(File.pathSeparator))
            }
            allArgs.addAll(info.compilerArgs)
            allArgs.addAll(info.sourceFiles)

            val pb = ProcessBuilder(allArgs)
//            info.directory?.let {
//                pb.directory(File(it))
//            }
            pb.inheritIO()
            val line = allArgs.joinToString(" ")
            log(2, "  Compiling ${line.forward()}")
            val process = pb.start()
            val errorCode = process.waitFor()

            return if (errorCode == 0) TaskResult(true, "Compilation succeeded")
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
