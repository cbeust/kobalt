package com.beust.kobalt.internal

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.ProjectDescription
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
abstract class JvmCompilerPlugin @Inject constructor(
        open val localRepo: LocalRepo,
        open val files: KFiles,
        open val depFactory: DepFactory,
        open val dependencyManager: DependencyManager,
        open val executors: KobaltExecutors,
        open val jvmCompiler: JvmCompiler) : BasePlugin() {

    companion object {
        @ExportedProjectProperty(doc = "The location of the build directory", type = "String")
        const val BUILD_DIR = "buildDir"

        const val TASK_CLEAN = "clean"
        const val TASK_TEST = "test"

        const val SOURCE_SET_MAIN = "main"
        const val SOURCE_SET_TEST = "test"
        const val DOCS_DIRECTORY = "docs/javadoc"
    }

    /**
     * Log with a project.
     */
    protected fun lp(project: Project, s: String) {
        log(2, "${project.name}: $s")
    }

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        project.projectProperties.put(BUILD_DIR, project.buildDirectory + File.separator + "classes")
    }

    /**
     * @return the test dependencies for this project, including the contributors.
     */
    protected fun testDependencies(project: Project) : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        result.add(FileDependency(makeOutputDir(project).absolutePath))
        result.add(FileDependency(makeOutputTestDir(project).absolutePath))
        with(project) {
            arrayListOf(compileDependencies, compileProvidedDependencies, testDependencies,
                    testProvidedDependencies).forEach {
                result.addAll(dependencyManager.calculateDependencies(project, context, it))
            }
        }
        return dependencyManager.reorderDependencies(result)
    }

    @Task(name = TASK_TEST, description = "Run the tests", runAfter = arrayOf("compile", "compileTest"))
    fun taskTest(project: Project) : TaskResult {
        lp(project, "Running tests")
        if (project.testDependencies.any { it.id.contains("testng")} ) {
            TestNgRunner(project, testDependencies(project)).runTests()
        } else {
            JUnitRunner(project, testDependencies(project)).runTests()
        }
        return TaskResult()
    }

    @Task(name = TASK_CLEAN, description = "Clean the project", runBefore = arrayOf("compile"))
    fun taskClean(project : Project ) : TaskResult {
        java.io.File(project.directory, project.buildDirectory).let { dir ->
            if (! dir.deleteRecursively()) {
                warn("Couldn't delete $dir")
            }
        }
        return TaskResult()
    }

    protected fun makeOutputDir(project: Project) : File = makeDir(project, KFiles.CLASSES_DIR)

    protected fun makeOutputTestDir(project: Project) : File = makeDir(project, KFiles.TEST_CLASSES_DIR)

    private fun makeDir(project: Project, suffix: String) : File {
        return File(project.directory, project.buildDirectory + File.separator + suffix).apply { mkdirs() }
    }

    /**
     * Copy the resources from a source directory to the build one
     */
    protected fun copyResources(project: Project, sourceSet: String) {
        val sourceDirs: ArrayList<String> = arrayListOf()
        var outputDir: String?
        if (sourceSet == JvmCompilerPlugin.SOURCE_SET_MAIN) {
            sourceDirs.addAll(project.sourceDirectories.filter { it.contains("resources") })
            outputDir = KFiles.CLASSES_DIR
        } else if (sourceSet == JvmCompilerPlugin.SOURCE_SET_TEST) {
            sourceDirs.addAll(project.sourceDirectoriesTest.filter { it.contains("resources") })
            outputDir = KFiles.TEST_CLASSES_DIR
        } else {
            throw IllegalArgumentException("Custom source sets not supported yet: $sourceSet")
        }

        if (sourceDirs.size > 0) {
            lp(project, "Copying $sourceSet resources")
            val absOutputDir = File(KFiles.joinDir(project.directory, project.buildDirectory!!, outputDir))
            sourceDirs.map { File(project.directory, it) }.filter {
                it.exists()
            } .forEach {
                log(2, "Copying from $sourceDirs to $absOutputDir")
                KFiles.copyRecursively(it, absOutputDir)
            }
        } else {
            lp(project, "No resources to copy for $sourceSet")
        }
    }

    protected val compilerArgs = arrayListOf<String>()

    fun addCompilerArgs(vararg args: String) {
        compilerArgs.addAll(args)
    }

    fun findSourceFiles(dir: String, sourceDirectories: Collection<String>): List<String> {
        val projectDir = File(dir)
        return files.findRecursively(projectDir,
                sourceDirectories.map { File(it) }) { it: String -> it.endsWith(".java") }
                .map { File(projectDir, it).absolutePath }
    }

    fun baseTaskCompile(project: Project, projectDescriptions: List<ProjectDescription>) : TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_MAIN)

        val projDeps = dependencyManager.dependentProjectDependencies(projectDescriptions, project, context)

        val classpath = dependencyManager.calculateDependencies(project, context, project.compileDependencies,
                project.compileProvidedDependencies, projDeps)

        val projectDirectory = File(project.directory)
        val buildDirectory = File(projectDirectory, project.buildDirectory + File.separator + "classes")
        buildDirectory.mkdirs()

        val sourceFiles = files.findRecursively(projectDirectory, project.sourceDirectories.map { File(it) },
                { it .endsWith(project.sourceSuffix) })
                .map { File(projectDirectory, it).absolutePath }

        val result = doCompile(project, classpath, sourceFiles, buildDirectory)
        return result
    }

    abstract fun doCompile(project: Project, classpath: List<IClasspathDependency>, sourceFiles: List<String>,
            buildDirectory: File) : TaskResult
}
