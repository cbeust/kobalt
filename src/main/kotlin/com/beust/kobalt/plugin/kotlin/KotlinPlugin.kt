package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KotlinPlugin @Inject constructor(
        override val localRepo: LocalRepo,
        override val files: KFiles,
        override val depFactory: DepFactory,
        override val dependencyManager: DependencyManager,
        override val executors: KobaltExecutors,
        override val jvmCompiler: JvmCompiler)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors, jvmCompiler) {

    init {
        Kobalt.registerCompiler(KotlinCompilerInfo())
    }

    companion object {
        public const val TASK_COMPILE: String = "compile"
        public const val TASK_COMPILE_TEST: String = "compileTest"
    }

    override val name = "kotlin"

    override fun accept(project: Project) = project is KotlinProject

    private val compilerArgs = arrayListOf<String>()

    @Task(name = TASK_COMPILE, description = "Compile the project")
    fun taskCompile(project: Project): TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_MAIN)
        val classpath = jvmCompiler.calculateDependencies(project, context!!, project.compileDependencies,
                project.compileProvidedDependencies)

        val projectDirectory = java.io.File(project.directory)
        val buildDirectory = File(projectDirectory, project.buildDirectory + File.separator + "classes")
        buildDirectory.mkdirs()

        val sourceFiles = files.findRecursively(projectDirectory,
                project.sourceDirectories.map { File(it) }, { it.endsWith(".kt") })
        val absoluteSourceFiles = sourceFiles.map {
            File(projectDirectory, it).absolutePath
        }

        compilePrivate(project, classpath, absoluteSourceFiles, buildDirectory)
        lp(project, "Compilation succeeded")
        return TaskResult()
    }

    fun addCompilerArgs(vararg args: String) {
        compilerArgs.addAll(args)
    }

    @Task(name = TASK_COMPILE_TEST, description = "Compile the tests", runAfter = arrayOf(TASK_COMPILE))
    fun taskCompileTest(project: Project): TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_TEST)
        val projectDir = File(project.directory)

        val absoluteSourceFiles = files.findRecursively(projectDir, project.sourceDirectoriesTest.map { File(it) })
            { it: String -> it.endsWith(".kt") }
                    .map { File(projectDir, it).absolutePath }

        compilePrivate(project, testDependencies(project),
                absoluteSourceFiles,
                makeOutputTestDir(project))

        lp(project, "Compilation of tests succeeded")
        return TaskResult()
    }

    private fun compilePrivate(project: Project, cpList: List<IClasspathDependency>, sources: List<String>,
            outputDirectory: File): TaskResult {
        return kotlinCompilePrivate {
            classpath(cpList.map { it.jarFile.get().absolutePath })
            sourceFiles(sources)
            compilerArgs(compilerArgs)
            output = outputDirectory
        }.compile(project, context)
    }
}

/**
 * @param project: the list of projects that need to be built before this one.
 */
@Directive
fun kotlinProject(vararg project: Project, init: KotlinProject.() -> Unit): KotlinProject {
    return KotlinProject().apply {
        init()
        Kobalt.declareProjectDependencies(this, project)
    }
}

class KotlinCompilerConfig {
    fun args(vararg options: String) {
        (Kobalt.findPlugin("kotlin") as KotlinPlugin).addCompilerArgs(*options)
    }
}

@Directive
fun Project.kotlinCompiler(init: KotlinCompilerConfig.() -> Unit) : KotlinCompilerConfig {
    with (KotlinCompilerConfig()) {
        init()
        return this
    }
}
