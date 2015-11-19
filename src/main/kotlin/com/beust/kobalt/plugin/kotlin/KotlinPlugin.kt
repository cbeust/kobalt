package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.IClasspathContributor
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.CompilerActionInfo
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.warn
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
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors, jvmCompiler),
            IClasspathContributor {

    companion object {
        public const val TASK_COMPILE: String = "compile"
        public const val TASK_COMPILE_TEST: String = "compileTest"
    }

    override val name = "kotlin"

    override fun accept(project: Project) = project is KotlinProject

    override fun doCompile(project: Project, cai: CompilerActionInfo) : TaskResult {
        val result =
                if (cai.sourceFiles.size > 0) {
                    compilePrivate(project, cai.dependencies, cai.sourceFiles, cai.outputDir)
                    lp(project, "Compilation succeeded")
                    TaskResult()
                } else {
                    warn("Couldn't find any source files")
                    TaskResult()
                }
        return result
    }

    override fun doJavadoc(project: Project, cai: CompilerActionInfo) : TaskResult {
        warn("javadoc task not implemented for Kotlin, call the dokka task instead")
        return TaskResult()
    }

        @Task(name = TASK_COMPILE_TEST, description = "Compile the tests", runAfter = arrayOf(TASK_COMPILE))
    fun taskCompileTest(project: Project): TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_TEST)
        val projectDir = File(project.directory)

        val sourceFiles = files.findRecursively(projectDir, project.sourceDirectoriesTest.map { File(it) })
            { it: String -> it.endsWith(project.sourceSuffix) }
                    .map { File(projectDir, it).absolutePath }

        val result =
            if (sourceFiles.size > 0) {
                compilePrivate(project, testDependencies(project),
                        sourceFiles,
                        makeOutputTestDir(project))
            } else {
                warn("Couldn't find any test files")
                TaskResult()
            }

        lp(project, "Compilation of tests succeeded")
        return result
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

    private fun getKotlinCompilerJar(name: String) : String {
        val id = "org.jetbrains.kotlin:$name:${KotlinCompiler.KOTLIN_VERSION}"
        val dep = MavenDependency.create(id, executors.miscExecutor)
        val result = dep.jarFile.get().absolutePath
        return result
    }


    // interface IClasspathContributor
    override fun entriesFor(project: Project?) : List<IClasspathDependency> =
        if (project == null || project is KotlinProject) {
            // All Kotlin projects automatically get the Kotlin runtime added to their class path
            listOf(getKotlinCompilerJar("kotlin-stdlib"), getKotlinCompilerJar("kotlin-compiler-embeddable"))
                .map { FileDependency(it) }
        } else {
            listOf()
        }
}

/**
 * @param project: the list of projects that need to be built before this one.
 */
@Directive
fun kotlinProject(vararg project: Project, init: KotlinProject.() -> Unit): KotlinProject {
    return KotlinProject().apply {
        init()
        (Kobalt.findPlugin("kotlin") as BasePlugin).addProject(this, project)
    }
}

class KotlinCompilerConfig {
    fun args(vararg options: String) {
        (Kobalt.findPlugin("kotlin") as JvmCompilerPlugin).addCompilerArgs(*options)
    }
}

@Directive
fun Project.kotlinCompiler(init: KotlinCompilerConfig.() -> Unit) = KotlinCompilerConfig().init()
