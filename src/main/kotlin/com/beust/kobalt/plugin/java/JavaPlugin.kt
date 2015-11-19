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
public class JavaPlugin @Inject constructor(
        override val localRepo: LocalRepo,
        override val files: KFiles,
        override val depFactory: DepFactory,
        override val dependencyManager: DependencyManager,
        override val executors: KobaltExecutors,
        val javaCompiler: JavaCompiler,
        override val jvmCompiler: JvmCompiler)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors, jvmCompiler) {
    companion object {
        public const val TASK_COMPILE : String = "compile"
        public const val TASK_JAVADOC : String = "javadoc"
        public const val TASK_COMPILE_TEST: String = "compileTest"
    }

    override val name = "java"

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
                    javaCompiler.javadoc(project, context, cai.copy(compilerArgs = compilerArgs))
                } else {
                    warn("Couldn't find any source files to run Javadoc on")
                    TaskResult()
                }
        return result
    }

    override fun doCompile(project: Project, cai: CompilerActionInfo) : TaskResult {
        val result =
            if (cai.sourceFiles.size > 0) {
                javaCompiler.compile(project, context, cai.copy(compilerArgs = compilerArgs))
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
                val buildDir = makeOutputTestDir(project)
                javaCompiler.compile(project, context, CompilerActionInfo(project.directory, testDependencies(project),
                        sourceFiles, buildDir, compilerArgs))
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
        (Kobalt.findPlugin("java") as BasePlugin).addProject(this, project)
    }
}

class JavaCompilerConfig {
    fun args(vararg options: String) {
        (Kobalt.findPlugin("java") as JvmCompilerPlugin).addCompilerArgs(*options)
    }
}

@Directive
fun Project.javaCompiler(init: JavaCompilerConfig.() -> Unit) = JavaCompilerConfig().init()

