package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.beust.kobalt.plugin.java.JavaPlugin
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
        open val jvmCompiler: JvmCompiler) : BasePlugin(), IProjectContributor {

    companion object {
        @ExportedProjectProperty(doc = "Projects this project depends on", type = "List<ProjectDescription>")
        const val DEPENDENT_PROJECTS = "dependentProjects"

        @ExportedProjectProperty(doc = "Compiler args", type = "List<String>")
        const val COMPILER_ARGS = "compilerArgs"

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
        project.projectProperties.put(DEPENDENT_PROJECTS, projects())
        addVariantTasks(project, "compile", runTask = { taskCompile(project) })
    }

    @Task(name = TASK_TEST, description = "Run the tests", runAfter = arrayOf("compile", "compileTest"))
    fun taskTest(project: Project) : TaskResult {
        lp(project, "Running tests")

        val runContributor = ActorUtils.selectAffinityActor(project, context,
                context.pluginInfo.testRunnerContributors)
        if (runContributor != null && runContributor.affinity(project, context) > 0) {
            return runContributor.run(project, context, dependencyManager.testDependencies(project, context,
                    projects()))
        } else {
            log(2, "Couldn't find a test runner for project ${project.name}, not running any tests")
            return TaskResult()
        }
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
            val absOutputDir = File(KFiles.joinDir(project.directory, project.buildDirectory, outputDir))
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

    protected fun compilerArgsFor(project: Project) : List<String> {
        val result = project.projectProperties.get(COMPILER_ARGS)
        if (result != null) {
            @Suppress("UNCHECKED_CAST")
            return result as List<String>
        } else {
            return emptyList()
        }
    }

    fun addCompilerArgs(project: Project, vararg args: String) {
        project.projectProperties.put(COMPILER_ARGS, arrayListOf(*args))
    }

    fun findSourceFiles(project: Project, context: KobaltContext, dir: String,
            sourceDirectories: Collection<String>): List<String> {
        val projectDir = File(dir)
        val allSourceDirectories = arrayListOf<File>()
        allSourceDirectories.addAll(sourceDirectories.map { File(it) })
        context.pluginInfo.sourceDirContributors.forEach {
            allSourceDirectories.addAll(it.sourceDirectoriesFor(project, context))
        }
        return files.findRecursively(projectDir, allSourceDirectories) {
                it: String -> it.endsWith(".java")
            }.map { File(projectDir, it).absolutePath }
    }

    override fun projects() = projects

    @Task(name = JavaPlugin.TASK_COMPILE, description = "Compile the project")
    fun taskCompile(project: Project) : TaskResult {
        val generatedDir = context.variant.maybeGenerateBuildConfig(project, context)
        val info = createCompilerActionInfo(project, context, generatedDir)
        val compiler = ActorUtils.selectAffinityActor(project, context, context.pluginInfo.compilerContributors)
        if (compiler != null) {
            return compiler.compile(project, context, info)
        } else {
            throw KobaltException("Couldn't find any compiler for project ${project.name}")
        }
    }

    @Task(name = "doc", description = "Generate the documentation for the project")
    fun taskJavadoc(project: Project) : TaskResult {
        val docGenerator = ActorUtils.selectAffinityActor(project, context, context.pluginInfo.docContributors)
        if (docGenerator != null) {
            return docGenerator.generateDoc(project, context, createCompilerActionInfo(project, context, null))
        } else {
            warn("Couldn't find any doc contributor for project ${project.name}")
            return TaskResult()
        }
    }

    private fun createCompilerActionInfo(project: Project, context: KobaltContext, generatedSourceDir: File?)
            : CompilerActionInfo {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_MAIN)

        val classpath = dependencyManager.calculateDependencies(project, context, projects,
                project.compileDependencies)

        val projectDirectory = File(project.directory)
        val buildDirectory = File(project.classesDir(context))
        buildDirectory.mkdirs()

        val initialSourceDirectories = arrayListOf<File>()

        // Add the generated source dir if any
        generatedSourceDir?.let {
            initialSourceDirectories.add(it)
        }

        // Source directories from the project and variants
        initialSourceDirectories.addAll(context.variant.sourceDirectories(project))

        // Source directories from the contributors
        context.pluginInfo.sourceDirContributors.forEach {
            initialSourceDirectories.addAll(it.sourceDirectoriesFor(project, context))
        }

        // Transform them with the interceptors, if any
        val sourceDirectories = context.pluginInfo.sourceDirectoriesInterceptors.fold(initialSourceDirectories.toList(),
                { sd, interceptor -> interceptor.intercept(project, context, sd) })

        // Now that we have the final list of source dirs, find source files in them
        val sourceFiles = files.findRecursively(projectDirectory, sourceDirectories,
                { it .endsWith(project.sourceSuffix) })
                .map { File(projectDirectory, it).path }

        val initialActionInfo = CompilerActionInfo(projectDirectory.path, classpath, sourceFiles, buildDirectory,
                emptyList())
        val result = context.pluginInfo.compilerInterceptors.fold(initialActionInfo, { ai, interceptor ->
            interceptor.intercept(project, context, ai)
        })
        return result
    }
}

