package com.beust.kobalt.plugin.groovy

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.homeDir
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.net.URLClassLoader

@Singleton
class GroovyPlugin @Inject constructor(val groovyCompiler: GroovyCompiler) : ICompilerContributor {
    override fun affinity(project: Project, context: KobaltContext) =
            if (hasSourceFiles(project)) 1 else 0

    // ICompilerContributor
    val compiler = object: ICompiler {
        override val sourceSuffixes = GroovyCompiler.SUFFIXES

        override val sourceDirectory = "groovy"

        override val priority = 1

        override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult {
            val result =
                if (info.sourceFiles.size > 0) {
                    groovyCompiler.compile(project, context, info)
                } else {
                    warn("Couldn't find any source files to compile")
                    TaskResult()
                }
            return result
        }
    }

    override fun compilersFor(project: Project, context: KobaltContext) = listOf(compiler)

    private fun hasSourceFiles(project: Project)
        = KFiles.findSourceFiles(project.directory, project.sourceDirectories, GroovyCompiler.SUFFIXES).size > 0
}

class GroovyCompiler @Inject constructor(dependencyManager: DependencyManager){
    companion object {
        val SUFFIXES = listOf("groovy")
        val GROOVY_HOME = homeDir("java/groovy-2.4.7")
        val GROOVYC = KFiles.joinDir(GROOVY_HOME, "bin/groovyc")
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

    fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult {
        val size = info.sourceFiles.size
        log(1, "Groovy compiling " + size + " " + Strings.pluralize(size, "file"))
        val result = invokeGroovyCompiler(info)
        return result
    }
}
