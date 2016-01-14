package com.beust.kobalt.plugin.java

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.warn
import java.io.File
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
        override val jvmCompiler: JvmCompiler,
        override val taskContributor : TaskContributor)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors, jvmCompiler, taskContributor),
            ICompilerContributor, IDocContributor, ITestSourceDirectoryContributor {
    companion object {
        const val PLUGIN_NAME = "Java"
    }

    override val name = PLUGIN_NAME

    override fun accept(project: Project) = project is JavaProject

    // IDocContributor
    override fun affinity(project: Project, context: KobaltContext) =
            if (project.sourceSuffix == ".java") 1 else 0

    override fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val result =
            if (info.sourceFiles.size > 0) {
                javaCompiler.javadoc(project, context, info.copy(compilerArgs = compilerArgsFor(project)))
            } else {
                warn("Couldn't find any source files to run Javadoc on")
                TaskResult()
            }
        return result
    }

    override fun doTaskCompileTest(project: Project): TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_TEST)
        val compilerActionInfo = createCompilerActionInfo(project, context, isTest = true)
        val result = javaCompiler.compile(project, context, compilerActionInfo)
        return result
    }

    // ICompilerContributor
    override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val result =
            if (info.sourceFiles.size > 0) {
                javaCompiler.compile(project, context, info.copy(compilerArgs = compilerArgsFor(project)))
            } else {
                warn("Couldn't find any source files to compile")
                TaskResult()
            }
        return result
    }

    // ITestSourceDirectoryContributor
    override fun testSourceDirectoriesFor(project: Project, context: KobaltContext)
        = project.sourceDirectoriesTest.map { File(it) }.toList()
}

@Directive
public fun javaProject(vararg projects: Project, init: JavaProject.() -> Unit): JavaProject {
    return JavaProject().apply {
        init()
        (Kobalt.findPlugin(JavaPlugin.PLUGIN_NAME) as JvmCompilerPlugin).addDependentProjects(this, projects.toList())
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

