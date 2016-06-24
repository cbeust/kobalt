package com.beust.kobalt.plugin.groovy

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.CompilerActionInfo
import com.beust.kobalt.api.ICompilerContributor
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.CompilerDescription
import com.beust.kobalt.plugin.ICompiler
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.net.URLClassLoader

@Singleton
class GroovyPlugin @Inject constructor(val groovyCompiler: GroovyCompiler) : ICompilerContributor {
    override fun affinity(project: Project, context: KobaltContext) =
            if (hasSourceFiles(project)) 1 else 0

    // ICompilerContributor
    val compiler = CompilerDescription(GroovyCompiler.SUFFIXES, "groovy", groovyCompiler)

    override fun compilersFor(project: Project, context: KobaltContext) = listOf(compiler)

    private fun hasSourceFiles(project: Project)
        = KFiles.findSourceFiles(project.directory, project.sourceDirectories, GroovyCompiler.SUFFIXES).size > 0
}

class GroovyCompiler @Inject constructor(dependencyManager: DependencyManager) : ICompiler {
    companion object {
        val SUFFIXES = listOf("groovy")
    }

    private val groovyCompilerClass: Class<*> by lazy {
        val jarFile = dependencyManager.create("org.codehaus.groovy:groovy:2.4.7").jarFile.get()
        val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()))
        classLoader.loadClass("org.codehaus.groovy.tools.FileSystemCompiler")
    }

    private fun invokeGroovyCompiler(info: CompilerActionInfo) : TaskResult {
        val cls = groovyCompilerClass
        val main = cls.getMethod("commandLineCompile", Array<String>::class.java)
        val classpath = info.dependencies.map { it.jarFile.get() }.joinToString(File.pathSeparator)
        try {
            main.invoke(null, arrayOf("-classpath", classpath, "-d", info.outputDir.path,
                    *info.sourceFiles.toTypedArray()))
            return TaskResult()
        } catch(ex: Exception) {
            return TaskResult(success = false, errorMessage = ex.cause.toString())
        }
    }

    override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult {
        val size = info.sourceFiles.size
        log(1, "Groovy compiling " + size + " " + Strings.pluralize(size, "file"))
        val result = invokeGroovyCompiler(info)
        return result
    }
}
