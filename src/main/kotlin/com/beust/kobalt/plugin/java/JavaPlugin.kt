package com.beust.kobalt.plugin.java

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.kotlin.KotlinCompilerConfig
import com.beust.kobalt.plugin.kotlin.KotlinPlugin
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
        override val executors: KobaltExecutors)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors) {

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
        if (cpList.size > 0) {
            val fullClasspath = dependencyManager.transitiveClosure(cpList)
            val stringClasspath = fullClasspath.map { it.jarFile.get().absolutePath }
            validateClasspath(stringClasspath)
            args.add("-classpath")
            args.add(stringClasspath.joinToString(File.pathSeparator))
        }
        args.addAll(compilerArgs)
        args.addAll(sourceFiles)

        val pb = ProcessBuilder(args)
        pb.directory(File(project.directory))
        pb.inheritIO()
        //        pb.redirectErrorStream(true)
        //        pb.redirectError(File("/tmp/kobalt-err"))
        //        pb.redirectOutput(File("/tmp/kobalt-out"))
        val line = args.joinToString(" ")
        log(1, "  Compiling ${sourceFiles.size} files")
        log(2, "  Compiling $project:\n$line")
        val process = pb.start()
        val errorCode = process.waitFor()

        return if (errorCode == 0) TaskResult(true, "Compilation succeeded")
            else TaskResult(false, "There were errors")
    }

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
                "-classpath", classpath.map { it.jarFile.get().absolutePath }.joinToString(File.pathSeparator),
                "-d", outputDir.absolutePath)
        val compressed = sourcesToDirectories(sourceFiles, project.sourceSuffix)
        args.addAll(compressed)

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

        val result =
            if (absoluteSourceFiles.size > 0) {
                compilePrivate(project,
                        testDependencies(project),
                        absoluteSourceFiles,
                        makeOutputTestDir(project))
            } else {
                // No files to compile
                TaskResult()
            }
        return result
    }

    private val compilerArgs = arrayListOf<String>()

    fun addCompilerArgs(vararg args: String) {
        compilerArgs.addAll(args)
    }

}

@Directive
public fun javaProject(init: JavaProject.() -> Unit): JavaProject {
    val pd = JavaProject()
    pd.init()
    return pd
}

class JavaCompilerConfig {
    fun args(vararg options: String) {
        (Kobalt.findPlugin("java") as JavaPlugin).addCompilerArgs(*options)
    }
}

@Directive
fun Project.javaCompiler(init: JavaCompilerConfig.() -> Unit) : JavaCompilerConfig {
    with (JavaCompilerConfig()) {
        init()
        return this
    }
}

