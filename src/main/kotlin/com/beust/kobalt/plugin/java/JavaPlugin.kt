package com.beust.kobalt.plugin.java

import com.beust.kobalt.api.ICompilerInfo
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.KobaltLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class JavaPlugin @Inject constructor(
        override val localRepo: LocalRepo,
        override val files: KFiles,
        override val depFactory: DepFactory,
        override val dependencyManager: DependencyManager,
        override val executors: KobaltExecutors)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors), KobaltLogger {

    init {
        Kobalt.registerCompiler(JavaCompilerInfo())
    }

    companion object {
        public const val TASK_COMPILE : String = "compile"
        public const val TASK_JAVADOC : String = "javadoc"
        public const val TASK_COMPILE_TEST: String = "compileTest"
    }

    override val name = "java"

    override fun accept(project: Project) = project is JavaProject

    private fun compilePrivate(project: Project, cpList: List<IClasspathDependency>, sourceFiles: List<String>,
            outputDirectory: File): TaskResult {
        outputDirectory.mkdirs()
        val jvm = JavaInfo.create(File(SystemProperties.javaBase))
        val javac = jvm.javacExecutable

        val args = arrayListOf(
                javac!!.absolutePath,
                "-d", outputDirectory.absolutePath)
        if (cpList.size() > 0) {
            args.add("-classpath")
            args.add(cpList.map { it.jarFile.get().absolutePath }.join(File.pathSeparator))
        }
        args.addAll(sourceFiles)

        val pb = ProcessBuilder(args)
        pb.directory(File(project.directory))
        pb.inheritIO()
        //        pb.redirectErrorStream(true)
        //        pb.redirectError(File("/tmp/kobalt-err"))
        //        pb.redirectOutput(File("/tmp/kobalt-out"))
        val line = args.join(" ")
        lp(project, "Compiling ${sourceFiles.size()} files with classpath size ${cpList.size()}")
        log(2, "Compiling ${project}:\n${line}")
        val process = pb.start()
        val errorCode = process.waitFor()

        return if (errorCode == 0) TaskResult(true, "Compilation succeeded")
            else TaskResult(false, "There were errors")
    }

    @Task(name = TASK_JAVADOC, description = "Run Javadoc")
    fun taskJavadoc(project: Project) : TaskResult {
        val projectDir = File(project.directory)
        val outputDir = File(projectDir,
                project.buildDirectory + File.separator + JvmCompilerPlugin.DOCS_DIRECTORY)
        outputDir.mkdirs()
        val jvm = JavaInfo.create(File(SystemProperties.javaBase))
        val javadoc = jvm.javadocExecutable

        val sourceFiles = files.findRecursively(projectDir, project.sourceDirectories.map { File(it) })
                { it: String -> it.endsWith(".java") }
                        .map { File(projectDir, it).absolutePath }
        val classpath = calculateClasspath(project.compileDependencies)
        val args = arrayListOf(
                javadoc!!.absolutePath,
                "-classpath", classpath.map { it.jarFile.get().absolutePath }.join(File.pathSeparator),
                "-d", outputDir.absolutePath)
        args.addAll(sourceFiles)

        val pb = ProcessBuilder(args)
        pb.directory(File(project.directory))
        pb.inheritIO()
        val process = pb.start()
        val errorCode = process.waitFor()

        return if (errorCode == 0) TaskResult(true, "Compilation succeeded")
        else TaskResult(false, "There were errors")

    }

    @Task(name = TASK_COMPILE, description = "Compile the project")
    fun taskCompile(project: Project) : TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_MAIN)
        val projectDir = File(project.directory)
        val buildDir = File(projectDir,
                project.buildDirectory + File.separator + "classes")
        val sourceFiles = files.findRecursively(projectDir, project.sourceDirectories.map { File(it) })
            { it: String -> it.endsWith(".java") }
                .map { File(projectDir, it).absolutePath }
        val classpath = calculateClasspath(project.compileDependencies)
        return compilePrivate(project, classpath, sourceFiles, buildDir)
    }

    @Task(name = TASK_COMPILE_TEST, description = "Compile the tests", runAfter = arrayOf("compile"))
    fun taskCompileTest(project: Project): TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_TEST)
        val projectDir = File(project.directory)

        val absoluteSourceFiles = files.findRecursively(projectDir, project.sourceDirectoriesTest.map { File(it) })
                { it: String -> it.endsWith(".java") }
                .map { File(projectDir, it).absolutePath }

        return compilePrivate(project,
                testDependencies(project),
                absoluteSourceFiles,
                makeOutputTestDir(project))
    }

}


@Directive
public fun javaProject(init: JavaProject.() -> Unit): JavaProject {
    val pd = JavaProject()
    pd.init()
    return pd
}
