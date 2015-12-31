package com.beust.kobalt.internal

import com.beust.kobalt.IncrementalTaskInfo
import com.beust.kobalt.KobaltException
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.Md5
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Base classes for plug-ins that compile files on the JVM. This base class requires the bare minimum
 * contributors (source files, projects and tasks). Subclasses can add more as they see fit (e.g. test
 * source directory, etc...).
 */
@Singleton
abstract class JvmCompilerPlugin @Inject constructor(
        open val localRepo: LocalRepo,
        open val files: KFiles,
        open val depFactory: DepFactory,
        open val dependencyManager: DependencyManager,
        open val executors: KobaltExecutors,
        open val jvmCompiler: JvmCompiler,
        open val taskContributor : TaskContributor)
            : BasePlugin(), ISourceDirectoryContributor, IProjectContributor, ITaskContributor by taskContributor {

    companion object {
        @ExportedProjectProperty(doc = "Projects this project depends on", type = "List<ProjectDescription>")
        const val DEPENDENT_PROJECTS = "dependentProjects"

        @ExportedProjectProperty(doc = "Compiler args", type = "List<String>")
        const val COMPILER_ARGS = "compilerArgs"

        const val TASK_COMPILE = "compile"
        const val TASK_COMPILE_TEST = "compileTest"
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
        taskContributor.addIncrementalVariantTasks(this, project, context, "compile",
                runTask = { taskCompile(project) })
    }

    @Task(name = TASK_TEST, description = "Run the tests",
            runAfter = arrayOf(JvmCompilerPlugin.TASK_COMPILE, JvmCompilerPlugin.TASK_COMPILE_TEST))
    fun taskTest(project: Project) : TaskResult {
        lp(project, "Running tests")

        val runContributor = ActorUtils.selectAffinityActor(project, context,
                context.pluginInfo.testRunnerContributors)
        if (runContributor != null && runContributor.affinity(project, context) > 0) {
            return runContributor.run(project, context, dependencyManager.testDependencies(project, context,
                    projects()))
        } else {
            log(1, "Couldn't find a test runner for project ${project.name}, not running any tests")
            return TaskResult()
        }
    }

    @Task(name = TASK_CLEAN, description = "Clean the project")
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
                KFiles.copyRecursively(it, absOutputDir, deleteFirst = false)
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

    open fun toClassFile(sourceFile: String) = sourceFile + ".class"

    fun addCompilerArgs(project: Project, vararg args: String) {
        project.projectProperties.put(COMPILER_ARGS, arrayListOf(*args))
    }

    fun isOutdated(project: Project, context: KobaltContext, actionInfo: CompilerActionInfo) : Boolean {
        fun stripSourceDir(sourceFile: String) : String {
            project.sourceDirectories.forEach {
                val d = listOf(project.directory, it).joinToString("/")
                if (sourceFile.startsWith(d)) return sourceFile.substring(d.length + 1)
            }
            throw KobaltException("Couldn't strip source dir from $sourceFile")
        }

        fun stripSuffix(    sourceFile: String) : String {
            val index = sourceFile.indexOf(project.sourceSuffix)
            if (index >= 0) return sourceFile.substring(0, index)
            else return sourceFile
        }

        actionInfo.sourceFiles.map { it.replace("\\", "/") }.forEach { sourceFile ->
            val stripped = stripSourceDir(sourceFile)
            val classFile = File(KFiles.joinDir(project.directory, project.classesDir(context),
                    toClassFile(stripSuffix(stripped))))
            if (! classFile.exists() || File(sourceFile).lastModified() > classFile.lastModified()) {
                log(2, "Outdated $sourceFile $classFile " + Date(File(sourceFile).lastModified()) +
                    " " + classFile.lastModified())
                return true
            }
        }
        return false
    }

    @IncrementalTask(name = JvmCompilerPlugin.TASK_COMPILE, description = "Compile the project")
    fun taskCompile(project: Project) : IncrementalTaskInfo {
        val inputChecksum = Md5.toMd5Directories(project.sourceDirectories.map {
            File(project.directory, it)
        })
        return IncrementalTaskInfo(
                inputChecksum = inputChecksum,
                outputChecksum = {
                    Md5.toMd5Directories(listOf(File(project.classesDir(context))))
                },
                task = { project -> doTaskCompile(project) }
        )
    }

    private fun doTaskCompile(project: Project) : TaskResult {
        // Set up the source files now that we have the variant
        sourceDirectories.addAll(context.variant.sourceDirectories(project))

        val sourceDirectory = context.variant.maybeGenerateBuildConfig(project, context)
        if (sourceDirectory != null) {
            sourceDirectories.add(sourceDirectory)
        }
        val info = createCompilerActionInfo(project, context, isTest = false)
        val compiler = ActorUtils.selectAffinityActor(project, context, context.pluginInfo.compilerContributors)
        if (compiler != null) {
            return compiler.compile(project, context, info)
        } else {
            throw KobaltException("Couldn't find any compiler for project ${project.name}")
        }
    }

    override fun projects() = projects

    @Task(name = "doc", description = "Generate the documentation for the project")
    fun taskJavadoc(project: Project) : TaskResult {
        val docGenerator = ActorUtils.selectAffinityActor(project, context, context.pluginInfo.docContributors)
        if (docGenerator != null) {
            return docGenerator.generateDoc(project, context, createCompilerActionInfo(project, context,
                    isTest = false))
        } else {
            warn("Couldn't find any doc contributor for project ${project.name}")
            return TaskResult()
        }
    }

    /**
     * Create a CompilerActionInfo (all the information that a compiler needs to know) for the given parameters.
     * Runs all the contributors and interceptors relevant to that task.
     */
    protected fun createCompilerActionInfo(project: Project, context: KobaltContext, isTest: Boolean) :
            CompilerActionInfo {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_MAIN)

        val classpath = if (isTest)
                dependencyManager.testDependencies(project, context, projects)
            else
                dependencyManager.dependencies(project, context, projects)

        val projectDirectory = File(project.directory)
        val buildDirectory = if (isTest) KFiles.makeOutputTestDir(project)
                else File(project.classesDir(context))
        buildDirectory.mkdirs()

        val initialSourceDirectories = arrayListOf<File>()

        // Source directories from the contributors
        initialSourceDirectories.addAll(
            if (isTest) {
                context.pluginInfo.testSourceDirContributors.flatMap { it.testSourceDirectoriesFor(project, context) }
            } else {
                context.pluginInfo.sourceDirContributors.flatMap { it.sourceDirectoriesFor(project, context) }
            })

        // Transform them with the interceptors, if any
        val sourceDirectories = if (isTest) {
            initialSourceDirectories
            } else {
                context.pluginInfo.sourceDirectoriesInterceptors.fold(initialSourceDirectories.toList(),
                        { sd, interceptor -> interceptor.intercept(project, context, sd) })
            }.filter {
                File(project.directory, it.path).exists()
            }

        // Now that we have the final list of source dirs, find source files in them
        val sourceFiles = files.findRecursively(projectDirectory, sourceDirectories,
                { it .endsWith(project.sourceSuffix) })
                .map { File(projectDirectory, it).path }

        // Finally, alter the info with the compiler interceptors before returning it
        val initialActionInfo = CompilerActionInfo(projectDirectory.path, classpath, sourceFiles, buildDirectory,
                emptyList())
        val result = context.pluginInfo.compilerInterceptors.fold(initialActionInfo, { ai, interceptor ->
            interceptor.intercept(project, context, ai)
        })
        return result
    }

    val sourceDirectories = hashSetOf<File>()

    // ISourceDirectoryContributor
    override fun sourceDirectoriesFor(project: Project, context: KobaltContext)
            = if (accept(project)) sourceDirectories.toList() else arrayListOf()
}

