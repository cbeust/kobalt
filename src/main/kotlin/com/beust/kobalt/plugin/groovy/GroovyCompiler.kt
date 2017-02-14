package com.beust.kobalt.plugin.groovy

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.CompilerActionInfo
import com.beust.kobalt.api.ICompiler
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.ICompilerAction
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.ParallelLogger
import com.beust.kobalt.misc.Strings
import com.google.inject.Inject
import com.google.inject.Singleton
import org.codehaus.groovy.control.CompilerConfiguration
import java.io.File

@Singleton
class GroovyCompiler @Inject constructor(val kobaltLog: ParallelLogger, val jvmCompiler: JvmCompiler) : ICompiler {

    override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        kobaltLog.log(project.name, 1,
                "  Groovy compiling " + Strings.pluralizeAll(info.sourceFiles.size, "file"))

        val compilerAction = object: ICompilerAction {
            override fun compile(project: Project?, info: CompilerActionInfo): TaskResult {
                val groovyClasspath = info.dependencies.map { it.jarFile.get().absolutePath }
                val compiler = org.codehaus.groovy.tools.Compiler(CompilerConfiguration().apply {
                    targetDirectory = info.outputDir
                    setClasspathList(groovyClasspath)
                    verbose = true
                    debug = true
                })
                // Will throw if there are any errors
                compiler.compile(info.sourceFiles.map(::File).toTypedArray())
                return TaskResult()
            }
        }

        val flags = listOf<String>()
        return jvmCompiler.doCompile(project, context, compilerAction, info, flags)
    }

}
