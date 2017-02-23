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
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.error
import com.beust.kobalt.misc.warn
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This plug-in takes care of compilation: it declares several common tasks ("compile", "compileTest")
 * and picks up all the compiler contributors in order to run them whenever a compilation is requested.
 */
@Singleton
open class JvmCompilerPlugin @Inject constructor(
        open val localRepo: LocalRepo,
        open val files: KFiles,
        open val dependencyManager: DependencyManager,
        open val executors: KobaltExecutors,
        open val taskContributor : TaskContributor,
        val compilerUtils: CompilerUtils)
            : BasePlugin(), ISourceDirectoryContributor, IProjectContributor, ITaskContributor by taskContributor {

    companion object {
        val PLUGIN_NAME = "JvmCompiler"

        @ExportedProjectProperty(doc = "Compiler args", type = "List<String>")
        const val COMPILER_ARGS = "compilerArgs"

        const val TASK_COMPILE = "compile"
        const val TASK_COMPILE_TEST = "compileTest"
        const val TASK_CLEAN = "clean"
        const val TASK_TEST = "test"

        const val DOCS_DIRECTORY = "docs/javadoc"

        const val GROUP_TEST = "test"
        const val GROUP_BUILD = "build"
        const val GROUP_DOCUMENTATION = "documentation"
    }

    override val name: String = PLUGIN_NAME

    override fun accept(project: Project) = true

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
//        cleanUpActors()
        taskContributor.addIncrementalVariantTasks(this, project, context, "compile", GROUP_BUILD,
                runTask = { taskCompile(project) })

        //
        // Add each test config as a test task. If none was specified, create a default one so that
        // users don't have to specify a test{}
        //
        if (project.testConfigs.isEmpty()) {
            project.testConfigs.add(TestConfig(project, isDefault = true))
        }
        project.testConfigs.forEach { config ->
            val taskName = if (config.name.isEmpty()) TASK_TEST else TASK_TEST + config.name

            taskManager.addTask(this, project, taskName, group = GROUP_TEST,
                    dependsOn = listOf(JvmCompilerPlugin.TASK_COMPILE, JvmCompilerPlugin.TASK_COMPILE_TEST),
                    task = { taskTest(project, config.name)} )
        }

    }

    private fun taskTest(project: Project, configName: String): TaskResult {
        context.logger.log(project.name, 2, "Running tests: $configName")

        val testContributor = ActorUtils.selectAffinityActor(project, context,
                context.pluginInfo.testRunnerContributors)
        if (testContributor != null && testContributor.affinity(project, context) > 0) {
//            val td1 = dependencyManager.testDependencies(project, context)
            val testDependencies = dependencyManager.calculateDependencies(project, context,
                    scopes = listOf(Scope.TEST))
            val compileDependencies = dependencyManager.calculateDependencies(project, context,
                    scopes = listOf(Scope.COMPILE))
            val allDependencies = (compileDependencies + testDependencies).toHashSet()
            return testContributor.run(project, context, configName, allDependencies.toList())
        } else {
            context.logger.log(project.name, 2,
                "Couldn't find a test runner for project ${project.name}, did you specify dependenciesTest{}?")
            return TaskResult()
        }
    }

    @Task(name = TASK_CLEAN, description = "Clean the project", group = GROUP_BUILD,
            runBefore = arrayOf(JvmCompilerPlugin.TASK_COMPILE))
    fun taskClean(project: Project): TaskResult {
        java.io.File(project.directory, project.buildDirectory).let { dir ->
            if (!dir.deleteRecursively()) {
                warn("Couldn't delete $dir")
            }
        }
        return TaskResult()
    }

    @IncrementalTask(name = TASK_COMPILE_TEST, description = "Compile the tests", group = GROUP_BUILD,
            dependsOn = arrayOf(TASK_COMPILE))
    fun taskCompileTest(project: Project): IncrementalTaskInfo {
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

    private fun sourceDirectories(project: Project, context: KobaltContext, isTest: Boolean)
        = context.variant.sourceDirectories(project, context, SourceSet.of(isTest))

    @IncrementalTask(name = JvmCompilerPlugin.TASK_COMPILE, description = "Compile the project", group = GROUP_BUILD,
            runAfter = arrayOf(TASK_CLEAN))
    fun taskCompile(project: Project): IncrementalTaskInfo {
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
                ActorUtils.selectAffinityActors(project, context, context.pluginInfo.compilerContributors)

        var failedResult: TaskResult? = null
        if (compilerContributors.isEmpty()) {
            throw KobaltException("Couldn't find any compiler for project ${project.name}")
        } else {
            val allCompilers = compilerContributors.flatMap { it.compilersFor(project, context)}.sorted()

            /**
             * Swap the Java and Kotlin compilers from the list.
             */
            fun swapJavaAndKotlin(allCompilers: List<ICompilerDescription>): List<ICompilerDescription> {
                val result = ArrayList(allCompilers)
                var ik = -1
                var ij = -1
                allCompilers.withIndex().forEach { wi ->
                    if (wi.value.sourceSuffixes.contains("java")) ij = wi.index
                    if (wi.value.sourceSuffixes.contains("kt")) ik = wi.index
                }
                Collections.swap(result, ik, ij)
                return result
            }

            // If this project has a kapt{} directive, we want to run the Java compiler first
            val hasKapt = project.projectProperties.get("kaptConfig") != null
            val allCompilersSorted = if (hasKapt) swapJavaAndKotlin(allCompilers) else allCompilers
            var done = false
            // The directory where the classes get compiled
            val buildDirectory =
                    if (isTest) File(project.buildDirectory, KFiles.TEST_CLASSES_DIR)
                    else File(project.classesDir(context))

            allCompilersSorted.doWhile({ ! done }) { compiler ->
                val compilerResults = compilerUtils.invokeCompiler(project, context, compiler,
                        sourceDirectories(project, context, isTest), isTest, buildDirectory)
                results.addAll(compilerResults.successResults)
                if (failedResult == null) failedResult = compilerResults.failedResult
                compilerResults.failedResult?.let { failedResult ->
                    done = true
                    failedResult.errorMessage?.let { errorMessage ->
                        error(text = errorMessage)
                    }
                }
            }

            return if (failedResult != null) failedResult!!
                else if (results.size > 0) results[0]
                else TaskResult(true)
        }
    }

    private val allProjects = arrayListOf<ProjectDescription>()

    // IProjectContributor
    override fun projects() = allProjects

    override fun cleanUpActors() {
        allProjects.clear()
    }

    fun addDependentProjects(project: Project, dependents: List<Project>) {
        project.dependsOn.addAll(dependents)
        with(ProjectDescription(project, dependents)) {
            allProjects.add(this)
        }
    }

    @Task(name = "doc", description = "Generate the documentation for the project", group = GROUP_DOCUMENTATION)
    fun taskJavadoc(project: Project): TaskResult {
        val docGenerator = ActorUtils.selectAffinityActor(project, context, context.pluginInfo.docContributors)
        if (docGenerator != null) {
            val buildDirectory = File(project.buildDirectory, JvmCompilerPlugin.DOCS_DIRECTORY)
            val contributors =
                    ActorUtils.selectAffinityActors(project, context, context.pluginInfo.compilerContributors)
            var result: TaskResult? = null
            contributors.forEach {
                it.compilersFor(project, context).forEach { compiler ->
                    result = docGenerator.generateDoc(project, context,
                            compilerUtils.createCompilerActionInfo(project, context, compiler,
                                    isTest = false, sourceDirectories = sourceDirectories(project, context, false),
                            sourceSuffixes = compiler.sourceSuffixes, buildDirectory = buildDirectory))
                }
            }
            return result!!
        } else {
            warn("Couldn't find any doc contributor for project ${project.name}")
            return TaskResult()
        }
    }

    // ISourceDirectoryContributor
    override fun sourceDirectoriesFor(project: Project, context: KobaltContext)
        = if (accept(project)) {
                sourceDirectories(project, context, isTest = false)
            } else {
                arrayListOf()
            }

    open val compiler: ICompilerContributor? = null
}
