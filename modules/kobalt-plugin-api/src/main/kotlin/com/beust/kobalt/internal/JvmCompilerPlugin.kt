package com.beust.kobalt.internal

import com.beust.kobalt.IncrementalTaskInfo
import com.beust.kobalt.KobaltException
import com.beust.kobalt.TaskResult
import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
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
        open val dependencyManager: DependencyManager,
        open val executors: KobaltExecutors,
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
        cleanUpActors()
        taskContributor.addIncrementalVariantTasks(this, project, context, "compile",
                runTask = { taskCompile(project) })

        //
        // Add each test config as a test task. If none was specified, create a default one so that
        // users don't have to specify a test{}
        //
        if (project.testConfigs.isEmpty()) {
            project.testConfigs.add(TestConfig(project))
        }
        project.testConfigs.forEach { config ->
            val taskName = if (config.name.isEmpty()) TASK_TEST else TASK_TEST + config.name

            taskManager.addTask(this, project, taskName,
                    dependsOn = listOf(JvmCompilerPlugin.TASK_COMPILE, JvmCompilerPlugin.TASK_COMPILE_TEST),
                    task = { taskTest(project, config.name)} )
        }

    }

    private fun taskTest(project: Project, configName: String): TaskResult {
        lp(project, "Running tests: $configName")

        val runContributor = ActorUtils.selectAffinityActor(project, context,
                context.pluginInfo.testRunnerContributors)
        if (runContributor != null && runContributor.affinity(project, context) > 0) {
            return runContributor.run(project, context, configName, dependencyManager.testDependencies(project,
                    context))
        } else {
            log(1, "Couldn't find a test runner for project ${project.name}, did you specify a dependenciesTest{}?")
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
    protected fun copyResources(project: Project, sourceSet: SourceSet) {
        var outputDir = sourceSet.outputDir

        val variantSourceDirs = context.variant.resourceDirectories(project, sourceSet)
        if (variantSourceDirs.size > 0) {
            lp(project, "Copying $sourceSet resources")
            val absOutputDir = File(KFiles.joinDir(project.directory, project.buildDirectory, outputDir))
            variantSourceDirs.map { File(project.directory, it.path) }.filter {
                it.exists()
            }.forEach {
                log(2, "Copying from $it to $absOutputDir")
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

    @IncrementalTask(name = TASK_COMPILE_TEST, description = "Compile the tests",
            dependsOn = arrayOf(TASK_COMPILE))
    fun taskCompileTest(project: Project): IncrementalTaskInfo {
        sourceTestDirectories.addAll(context.variant.sourceDirectories(project, context, SourceSet.of(isTest = true)))
        return IncrementalTaskInfo(
            inputChecksum = {
                Md5.toMd5Directories(context.testSourceDirectories(project).map { File(project.directory, it.path)})
            },
            outputChecksum = {
                Md5.toMd5Directories(listOf(KFiles.makeOutputTestDir(project)))
            },
            task = { project -> doTaskCompileTest(project)},
            context = context
        )
    }

    @IncrementalTask(name = JvmCompilerPlugin.TASK_COMPILE, description = "Compile the project",
            runAfter = arrayOf(TASK_CLEAN))
    fun taskCompile(project: Project): IncrementalTaskInfo {
        // Generate the BuildConfig before invoking sourceDirectories() since that call
        // might add the buildConfig source directories
        val sourceDirectory = context.variant.maybeGenerateBuildConfig(project, context)
        if (sourceDirectory != null) {
            sourceDirectories.add(sourceDirectory)
        }

        // Set up the source files now that we have the variant
        sourceDirectories.addAll(context.variant.sourceDirectories(project, context, SourceSet.of(isTest = false)))

        return IncrementalTaskInfo(
                inputChecksum = {
                    Md5.toMd5Directories(context.sourceDirectories(project).map { File(project.directory, it.path) })
                },
                outputChecksum = {
                    Md5.toMd5Directories(listOf(File(project.directory, project.classesDir(context))))
                },
                task = { project -> doTaskCompile(project) },
                context = context
        )
    }

    private fun doTaskCompile(project: Project) = doTaskCompile(project, isTest = false)

    private fun doTaskCompileTest(project: Project) = doTaskCompile(project, isTest = true)

    private fun doTaskCompile(project: Project, isTest: Boolean): TaskResult {
        val results = arrayListOf<TaskResult>()

        val compilerContributors = context.pluginInfo.compilerContributors
                ActorUtils.selectAffinityActors(project, context,
                context.pluginInfo.compilerContributors)

        var failedResult: TaskResult? = null
        if (compilerContributors.isEmpty()) {
            throw KobaltException("Couldn't find any compiler for project ${project.name}")
        } else {
            val allCompilers = compilerContributors.flatMap { it.compilersFor(project, context)}.sorted()
            allCompilers.forEach { compiler ->
                val contributedSourceDirs =
                    if (isTest) {
                        context.testSourceDirectories(project)
                    } else {
                        context.sourceDirectories(project)
                    }
                val sourceFiles = KFiles.findSourceFiles(project.directory,
                        contributedSourceDirs.map { it.path }, compiler.sourceSuffixes)
                if (sourceFiles.size > 0) {
                    // TODO: createCompilerActionInfo recalculates the source files, only compute them
                    // once and pass them
                    val info = createCompilerActionInfo(project, context, compiler, isTest, sourceDirectories,
                            sourceSuffixes = compiler.sourceSuffixes)
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
                else if (results.size > 0) results[0]
                else TaskResult(true)
        }
    }

    val allProjects = arrayListOf<ProjectDescription>()

    // IProjectContributor
    override fun projects() = allProjects

    override fun cleanUpActors() {
        allProjects.clear()
    }

    fun addDependentProjects(project: Project, dependents: List<Project>) {
        project.projectExtra.dependsOn.addAll(dependents)
        with(ProjectDescription(project, dependents)) {
            allProjects.add(this)
        }

        project.projectProperties.put(DEPENDENT_PROJECTS, allProjects)
    }

    @Task(name = "doc", description = "Generate the documentation for the project")
    fun taskJavadoc(project: Project): TaskResult {
        val docGenerator = ActorUtils.selectAffinityActor(project, context, context.pluginInfo.docContributors)
        if (docGenerator != null) {
            val contributors =
                    ActorUtils.selectAffinityActors(project, context, context.pluginInfo.compilerContributors)
            var result: TaskResult? = null
            contributors.forEach {
                it.compilersFor(project, context).forEach { compiler ->
                    result = docGenerator.generateDoc(project, context, createCompilerActionInfo(project, context,
                            compiler,
                            isTest = false, sourceDirectories = sourceDirectories,
                            sourceSuffixes = compiler.sourceSuffixes))
                }
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
    protected fun createCompilerActionInfo(project: Project, context: KobaltContext, compiler: ICompiler,
            isTest: Boolean, sourceDirectories: List<File>, sourceSuffixes: List<String>): CompilerActionInfo {
        copyResources(project, SourceSet.of(isTest))

        val fullClasspath = if (isTest) dependencyManager.testDependencies(project, context)
            else dependencyManager.dependencies(project, context)

        // Remove all the excluded dependencies from the classpath
        val classpath = fullClasspath.filter {
            ! isDependencyExcluded(it, project.excludedDependencies)
        }

        val buildDirectory = if (isTest) File(project.buildDirectory, KFiles.TEST_CLASSES_DIR)
            else File(project.classesDir(context))
        buildDirectory.mkdirs()


        val initialSourceDirectories = ArrayList<File>(sourceDirectories)
        // Source directories from the contributors
        val contributedSourceDirs =
            if (isTest) {
                context.pluginInfo.testSourceDirContributors.flatMap { it.testSourceDirectoriesFor(project, context) }
            } else {
                context.pluginInfo.sourceDirContributors.flatMap { it.sourceDirectoriesFor(project, context) }
            }

        initialSourceDirectories.addAll(contributedSourceDirs)

        // Transform them with the interceptors, if any
        val allSourceDirectories =
            if (isTest) {
                initialSourceDirectories
            } else {
                context.pluginInfo.sourceDirectoriesInterceptors.fold(initialSourceDirectories.toList(),
                        { sd, interceptor -> interceptor.intercept(project, context, sd) })
            }.filter {
                File(project.directory, it.path).exists()
            }.filter {
                ! KFiles.isResource(it.path)
            }.distinct()

        // Now that we have all the source directories, find all the source files in them
        val projectDirectory = File(project.directory)
        val sourceFiles = if (compiler.canCompileDirectories) {
                allSourceDirectories.map { File(projectDirectory, it.path).path }
            } else {
                files.findRecursively(projectDirectory, allSourceDirectories,
                        { file -> sourceSuffixes.any { file.endsWith(it) } })
                        .map { File(projectDirectory, it).path }
            }

        // Special treatment if we are compiling Kotlin files and the project also has a java source
        // directory. In this case, also pass that java source directory to the Kotlin compiler as is
        // so that it can parse its symbols
        // Note: this should actually be queried on the compiler object so that this method, which
        // is compiler agnostic, doesn't hardcode Kotlin specific stuff
        val extraSourceFiles = arrayListOf<String>()
        if (sourceSuffixes.any { it.contains("kt")}) {
            project.sourceDirectories.forEach {
                val javaDir = KFiles.joinDir(project.directory, it)
                if (File(javaDir).exists()) {
                    if (it.contains("java")) {
                        extraSourceFiles.add(javaDir)
                        // Add all the source directories contributed as potential Java directories too
                        // (except our own)
                        context.pluginInfo.sourceDirContributors.filter { it != this }.forEach {
                            extraSourceFiles.addAll(it.sourceDirectoriesFor(project, context).map { it.path })
                        }

                    }
                }
            }
        }

        val allSources = (sourceFiles + extraSourceFiles).distinct().filter { File(it).exists() }

        // Finally, alter the info with the compiler interceptors before returning it
        val initialActionInfo = CompilerActionInfo(projectDirectory.path, classpath, allSources,
                sourceSuffixes, buildDirectory, emptyList() /* the flags will be provided by flag contributors */)
        val result = context.pluginInfo.compilerInterceptors.fold(initialActionInfo, { ai, interceptor ->
            interceptor.intercept(project, context, ai)
        })
        return result
    }

    val sourceDirectories = arrayListOf<File>()
    val sourceTestDirectories = arrayListOf<File>()

    // ISourceDirectoryContributor
    override fun sourceDirectoriesFor(project: Project, context: KobaltContext)
        = if (accept(project)) {
                sourceDirectories.toList()
            } else {
                arrayListOf()
            }

    open val compiler: ICompilerContributor? = null
}
