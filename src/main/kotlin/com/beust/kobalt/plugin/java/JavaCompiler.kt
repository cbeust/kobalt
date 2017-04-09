package com.beust.kobalt.plugin.java

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.internal.CompilerUtils
import com.beust.kobalt.internal.ICompilerAction
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.ParallelLogger
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.warn
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

@Singleton
class JavaCompiler @Inject constructor(val jvmCompiler: JvmCompiler, val kobaltLog: ParallelLogger,
        val compilerUtils: CompilerUtils) : ICompiler {
    fun compilerAction(executable: File) = object : ICompilerAction {
        override fun compile(project: Project?, info: CompilerActionInfo): TaskResult {
            val projectName = project?.name
            if (info.sourceFiles.isEmpty()) {
                warn("No source files to compile")
                return TaskResult()
            }

            val command: String
            val errorMessage: String
            val compiler = ToolProvider.getSystemJavaCompiler()
            fun logk(level: Int, message: CharSequence) = kobaltLog.log(projectName ?: "", level, message)
            val result =
                if (false) {
//                if (compiler != null) {
                    logk(2, "Found system Java compiler, using the compiler API")
                    val allArgs = arrayListOf(
                            "-d", KFiles.makeDir(info.directory!!, info.outputDir.path).path)
                    if (info.dependencies.isNotEmpty()) {
                        allArgs.add("-classpath")
                        allArgs.add(info.dependencies.map { it.jarFile.get() }.joinToString(File.pathSeparator))
                    }
                    allArgs.addAll(info.compilerArgs)

                    val fileManager = compiler.getStandardFileManager(null, null, null)
                    val fileObjects = fileManager.getJavaFileObjectsFromFiles(info.sourceFiles.map(::File).filter {
                            it.isFile
                        })
                    val dc = DiagnosticCollector<JavaFileObject>()
                    val classes = arrayListOf<String>()
                    val writer = PrintWriter(System.out)
                    val task = compiler.getTask(writer, fileManager, dc, allArgs, classes, fileObjects)

                    command = "javac " + allArgs.joinToString(" ") + " " + info.sourceFiles.joinToString(" ")
                    logk(2, "Launching\n$command")

                    kobaltLog.log(projectName!!, 1,
                            "  Java compiling " + Strings.pluralizeAll(info.sourceFiles.size, "file"))
                    val result = task.call()
                    errorMessage = dc.diagnostics.joinToString("\n")
                    result
                } else {
                    logk(2, "Forking $executable")
                    val allArgs = arrayListOf(
                            executable.absolutePath,
                            "-d", KFiles.makeDir(info.directory!!, info.outputDir.path).path)

                    if (info.dependencies.isNotEmpty()) {
                        allArgs.add("-classpath")
                        allArgs.add(info.dependencies.map { it.jarFile.get() }.joinToString(File.pathSeparator))
                    }

                    allArgs.addAll(info.compilerArgs)
                    allArgs.addAll(info.sourceFiles.filter { File(it).isFile })

                    val dir = Files.createTempDirectory("kobalt").toFile()
                    val atFile = File(dir, "javac-" + project?.name + ".txt")
                    atFile.writeText(KFiles.fixSlashes(allArgs.subList(1, allArgs.size).joinToString(" ")))
                    val pb = ProcessBuilder(executable.absolutePath, "@" + KFiles.fixSlashes(atFile))
                    pb.inheritIO()
                    logk(1, "  Java compiling " + Strings.pluralizeAll(info.sourceFiles.size, "file"))
                    logk(2, "  Java compiling file: " + KFiles.fixSlashes(atFile))

                    command = allArgs.joinToString(" ") + " " + info.sourceFiles.joinToString(" ")
                    val process = pb.start()
                    val errorCode = process.waitFor()
                    errorMessage = "Something went wrong running javac, need to switch to RunCommand"
                    errorCode == 0
                }

            return if (result) {
                    TaskResult(true, errorMessage = "Compilation succeeded")
                } else {
                    val message = "Compilation errors, command:\n$command\n" + errorMessage
                    logk(1, message)
                    TaskResult(false, errorMessage = message)
                }

        }
    }

    /**
     * Invoke the given executable on the CompilerActionInfo.
     */
    private fun run(project: Project?, context: KobaltContext?, cai: CompilerActionInfo, executable: File,
            flags: List<String>): TaskResult {
        return jvmCompiler.doCompile(project, context, compilerAction(executable), cai, flags)
    }

    override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val adapters = context.pluginInfo.compilerFlagContributors.map {
            val closure = { project: Project, context: KobaltContext, currentFlags: List<String>,
                suffixesBeingCompiled: List<String>
                    -> it.compilerFlagsFor(project, context, currentFlags, suffixesBeingCompiled) }
            FlagContributor(it.flagPriority, closure)
        }
        return run(project, context, info, JavaInfo.create(File(SystemProperties.javaBase)).javacExecutable!!,
                compilerUtils.compilerFlags(project, context, info, adapters))
    }

    fun javadoc(project: Project?, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val adapters = context.pluginInfo.docFlagContributors.map {
            val closure = { project: Project, context: KobaltContext, currentFlags: List<String>,
                    suffixesBeingCompiled: List<String>
                -> it.docFlagsFor(project, context, currentFlags, suffixesBeingCompiled) }
            FlagContributor(it.flagPriority, closure)
        }
        return run(project, context, info, JavaInfo.create(File(SystemProperties.javaBase)).javadocExecutable!!,
                compilerUtils.compilerFlags(project, context, info, adapters))
    }
}
