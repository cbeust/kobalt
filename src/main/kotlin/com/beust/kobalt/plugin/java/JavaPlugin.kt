package com.beust.kobalt.plugin.java

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.CompilerActionInfo
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.warn
import java.io.File
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaPlugin @Inject constructor(
        override val localRepo: LocalRepo,
        override val files: KFiles,
        override val depFactory: DepFactory,
        override val dependencyManager: DependencyManager,
        override val executors: KobaltExecutors,
        val javaCompiler: JavaCompiler,
        override val jvmCompiler: JvmCompiler)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors, jvmCompiler) {
    companion object {
        const val PLUGIN_NAME = "Java"
        const val TASK_COMPILE = "compile"
        const val TASK_JAVADOC = "javadoc"
        const val TASK_COMPILE_TEST = "compileTest"
    }

    override val name = PLUGIN_NAME

    override fun accept(project: Project) = project is JavaProject

    /**
     * Replace all the .java files with their directories + *.java in order to limit the
     * size of the command line (which blows up on Windows if there are a lot of files).
     */
    private fun sourcesToDirectories(sources: List<String>, suffix: String) : Collection<String> {
        val dirs = HashSet(sources.map {
            Paths.get(it).parent.toFile().path + File.separator + "*$suffix"
        })
        return dirs
    }

    override fun doJavadoc(project: Project, cai: CompilerActionInfo) : TaskResult {
        val result =
                if (cai.sourceFiles.size > 0) {
                    javaCompiler.javadoc(project, context, cai.copy(compilerArgs = compilerArgsFor(project)))
                } else {
                    warn("Couldn't find any source files to run Javadoc on")
                    TaskResult()
                }
        return result
    }

    override fun doCompile(project: Project, cai: CompilerActionInfo) : TaskResult {
        val result =
            if (cai.sourceFiles.size > 0) {
                javaCompiler.compile(project, context, cai.copy(compilerArgs = compilerArgsFor(project)))
            } else {
                warn("Couldn't find any source files to compile")
                TaskResult()
            }
        return result
    }

    @Task(name = TASK_COMPILE_TEST, description = "Compile the tests", runAfter = arrayOf("compile"))
    fun taskCompileTest(project: Project): TaskResult {
        val sourceFiles = findSourceFiles(project.directory, project.sourceDirectoriesTest)
        val result =
            if (sourceFiles.size > 0) {
                copyResources(project, JvmCompilerPlugin.SOURCE_SET_TEST)
                val buildDir = KFiles.makeOutputTestDir(project)
                javaCompiler.compile(project, context, CompilerActionInfo(project.directory,
                        dependencyManager.testDependencies(project, context, projects()),
                        sourceFiles, buildDir, compilerArgsFor(project)))
            } else {
                warn("Couldn't find any tests to compile")
                TaskResult()
            }
        return result
    }
}

@Directive
public fun javaProject(vararg project: Project, init: JavaProject.() -> Unit): JavaProject {
    return JavaProject().apply {
        init()
        (Kobalt.findPlugin(JavaPlugin.PLUGIN_NAME) as BasePlugin).addProject(this, project)
    }
}

class JavaCompilerConfig(val project: Project) {
    fun args(vararg options: String) {
        (Kobalt.findPlugin(JavaPlugin.PLUGIN_NAME) as JvmCompilerPlugin).addCompilerArgs(project, *options)
    }
}

@Directive
fun Project.javaCompiler(init: JavaCompilerConfig.() -> Unit) = let {
    JavaCompilerConfig(it).init()
}

