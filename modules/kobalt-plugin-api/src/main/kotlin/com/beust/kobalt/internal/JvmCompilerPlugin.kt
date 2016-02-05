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
 * This plug-in takes care of compilation: it declares a bunch of tasks ("compile", "compileTest") and
 * and picks up all the compiler contributors in order to run them whenever a compilation is requested.
 */
@Singleton
open class JvmCompilerPlugin @Inject constructor(
        open val localRepo: LocalRepo,
        open val files: KFiles,
        open val depFactory: DepFactory,
        open val dependencyManager: DependencyManager,
        open val executors: KobaltExecutors,
        open val jvmCompiler: JvmCompiler,
        open val taskContributor : TaskContributor)
            : BasePlugin(), ISourceDirectoryContributor, IProjectContributor, ITaskContributor by taskContributor {

    companion object {
        val PLUGIN_NAME = "JvmCompiler"

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

    override val name: String = PLUGIN_NAME

    override fun accept(project: Project) = true

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
    fun taskTest(project: Project): TaskResult {
        lp(project, "Running tests")

        val runContributor = ActorUtils.selectAffinityActor(project, context,
                context.pluginInfo.testRunnerContributors)
        if (runContributor != null && runContributor.affinity(project, context) > 0) {
            return runContributor.run(project, context, dependencyManager.testDependencies(project, context))
        } else {
            log(1, "Couldn't find a test runner for project ${project.name}, not running any tests")
            return TaskResult()
        }
    }

    @Task(name = TASK_CLEAN, description = "Clean the project")
    fun taskClean(project: Project): TaskResult {
        java.io.File(project.directory, project.buildDirectory).let { dir ->
            if (!dir.deleteRecursively()) {
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
            }.forEach {
                log(2, "Copying from $sourceDirs to $absOutputDir")
                KFiles.copyRecursively(it, absOutputDir, deleteFirst = false)
            }
        } else {
            lp(project, "No resources to copy for $sourceSet")
        }
    }

    protected fun compilerArgsFor(project: Project): List<String> {
        val result = project.projectProperties.get(COMPILER_ARGS)
        if (result != null) {
            @Suppress("UNCHECKED_CAST")
            return result as List<String>
        } else {
            return emptyList()
        }
    }

    @IncrementalTask(name = JvmCompilerPlugin.TASK_COMPILE, description = "Compile the project")
    fun taskCompile(project: Project): IncrementalTaskInfo {
        val inputChecksum = Md5.toMd5Directories(project.sourceDirectories.map {
            File(project.directory, it)
        })
        return IncrementalTaskInfo(
                inputChecksum = inputChecksum,
                outputChecksum = {
                    Md5.toMd5Directories(listOf(File(project.directory, project.classesDir(context))))
                },
                task = { project -> doTaskCompile(project) }
        )
    }

    private fun doTaskCompile(project: Project) = doTaskCompile(project, isTest = false)

    private fun doTaskCompileTest(project: Project) = doTaskCompile(project, isTest = true)

    private fun doTaskCompile(project: Project, isTest: Boolean): TaskResult {
        // Set up the source files now that we have the variant
        sourceDirectories.addAll(context.variant.sourceDirectories(project, context))

        val sourceDirectory = context.variant.maybeGenerateBuildConfig(project, context)
        if (sourceDirectory != null) {
            sourceDirectories.add(sourceDirectory)
        }
        val results = arrayListOf<TaskResult>()

        val compilers = ActorUtils.selectAffinityActors(project, context, context.pluginInfo.compilerContributors)

        var failedResult: TaskResult? = null
        if (compilers.isEmpty()) {
            throw KobaltException("Couldn't find any compiler for project ${project.name}")
        } else {
            compilers.forEach { compiler ->
                if (containsSourceFiles(project, compiler)) {
                    val info = createCompilerActionInfo(project, context, isTest, sourceSuffixes = compiler.sourceSuffixes)
                    val thisResult = compiler.compile(project, context, info)
                    results.add(thisResult)
                    if (!thisResult.success && failedResult == null) {
                        failedResult = thisResult
                    }
                } else {
                    log(2, "Compiler $compiler not running on ${project.name} since no source files were found")
                }
            }
            return if (failedResult != null) failedResult!!
            else results[0]
        }
    }

    private fun containsSourceFiles(project: Project, compiler: ICompilerContributor): Boolean {
        project.projectExtra.suffixesFound.forEach {
            if (compiler.sourceSuffixes.contains(it)) return true
        }
        return false
    }

    val allProjects = arrayListOf<ProjectDescription>()

    override fun projects() = allProjects

    fun addDependentProjects(project: Project, dependents: List<Project>) {
        project.projectExtra.dependsOn.addAll(dependents)
        with(ProjectDescription(project, dependents)) {
            allProjects.add(this)
        }
    }

    @Task(name = "doc", description = "Generate the documentation for the project")
    fun taskJavadoc(project: Project): TaskResult {
        val docGenerator = ActorUtils.selectAffinityActor(project, context, context.pluginInfo.docContributors)
        if (docGenerator != null) {
            val compilers = ActorUtils.selectAffinityActors(project, context, context.pluginInfo.compilerContributors)
            var result: TaskResult? = null
            compilers.forEach { compiler ->
                result = docGenerator.generateDoc(project, context, createCompilerActionInfo(project, context,
                        isTest = false, sourceSuffixes = compiler.sourceSuffixes))
            }
            return result!!
        } else {
            warn("Couldn't find any doc contributor for project ${project.name}")
            return TaskResult()
        }
    }

    /**
     * Naïve implementation: just exclude all dependencies that start with one of the excluded dependencies.
     * Should probably make exclusion more generic (full on string) or allow exclusion to be specified
     * formally by groupId or artifactId.
     */
    private fun isDependencyExcluded(id: IClasspathDependency, excluded: List<IClasspathDependency>)
            = excluded.any { id.id.startsWith(it.id) }

    /**
     * Create a CompilerActionInfo (all the information that a compiler needs to know) for the given parameters.
     * Runs all the contributors and interceptors relevant to that task.
     */
    protected fun createCompilerActionInfo(project: Project, context: KobaltContext, isTest: Boolean,
            sourceSuffixes: List<String>): CompilerActionInfo {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_MAIN)

        val fullClasspath = if (isTest) dependencyManager.testDependencies(project, context)
            else dependencyManager.dependencies(project, context)

        // Remove all the excluded dependencies from the classpath
        val classpath = fullClasspath.filter {
            ! isDependencyExcluded(it, project.excludedDependencies)
        }

        val projectDirectory = File(project.directory)
        val buildDirectory = if (isTest) File(project.buildDirectory, KFiles.TEST_CLASSES_DIR)
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

        val sourceFiles = files.findRecursively(projectDirectory, sourceDirectories,
                { file -> sourceSuffixes.any { file.endsWith(it) }})
                .map { File(projectDirectory, it).path }

        // Special treatment if we are compiling Kotlin files and the project also has a java source
        // directory. In this case, also pass that java source directory to the Kotlin compiler as is
        // so that it can parse its symbols
        val extraSourceFiles = arrayListOf<String>()
        if (sourceSuffixes.any { it.contains("kt")}) {
            project.sourceDirectories.forEach {
                if (it.contains("java")) extraSourceFiles.add(KFiles.joinDir(project.directory, it))
            }
        }

        // Finally, alter the info with the compiler interceptors before returning it
        val initialActionInfo = CompilerActionInfo(projectDirectory.path, classpath, sourceFiles + extraSourceFiles,
                buildDirectory, emptyList() /* the flags will be provided by flag contributors */)
        val result = context.pluginInfo.compilerInterceptors.fold(initialActionInfo, { ai, interceptor ->
            interceptor.intercept(project, context, ai)
        })
        return result
    }

    val sourceDirectories = hashSetOf<File>()

    // ISourceDirectoryContributor
    override fun sourceDirectoriesFor(project: Project, context: KobaltContext)
            = if (accept(project)) sourceDirectories.toList() else arrayListOf()

    @IncrementalTask(name = TASK_COMPILE_TEST, description = "Compile the tests",
            runAfter = arrayOf(JvmCompilerPlugin.TASK_COMPILE))
    fun taskCompileTest(project: Project): IncrementalTaskInfo {
        val inputChecksum = Md5.toMd5Directories(project.sourceDirectoriesTest.map {
            File(project.directory, it)
        })
        return IncrementalTaskInfo(
                inputChecksum = inputChecksum,
                outputChecksum = {
                    Md5.toMd5Directories(listOf(KFiles.makeOutputTestDir(project)))
                },
                task = { project -> doTaskCompileTest(project) }
        )
    }

    open val compiler: ICompilerContributor? = null
}
