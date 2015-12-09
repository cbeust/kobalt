package com.beust.kobalt.plugin.application

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.ActorUtils
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.RunCommand
import com.beust.kobalt.misc.warn
import com.beust.kobalt.plugin.packaging.PackageConfig
import com.beust.kobalt.plugin.packaging.PackagingPlugin
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File

@Directive
class ApplicationConfig {
    var mainClass: String? = null
    var jvmArgs = arrayListOf<String>()

    fun jvmArgs(vararg args: String) = args.forEach { jvmArgs.add(it) }
}

@Directive
fun Project.application(init: ApplicationConfig.() -> Unit) {
    ApplicationConfig().let { config ->
        config.init()
        (Plugins.findPlugin(ApplicationPlugin.PLUGIN_NAME) as ApplicationPlugin).addConfiguration(this, config)
    }
}

@Singleton
class ApplicationPlugin @Inject constructor(val executors: KobaltExecutors,
        val dependencyManager: DependencyManager)
            : ConfigPlugin<ApplicationConfig>(), IRunnerContributor, ITaskContributor {

    companion object {
        const val PLUGIN_NAME = "Application"
    }

    override val name = PLUGIN_NAME

    val taskContributor : TaskContributor = TaskContributor()

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        taskContributor.addVariantTasks(this, project, context, "run", runAfter = listOf("install"),
                runTask = { taskRun(project) })
    }

    @Task(name = "run", description = "Run the main class", runAfter = arrayOf("install"))
    fun taskRun(project: Project): TaskResult {
        val runContributor = ActorUtils.selectAffinityActor(project, context,
                context.pluginInfo.runnerContributors)
        if (runContributor != null && runContributor.affinity(project, context) > 0) {
            return runContributor.run(project, context, dependencyManager.dependencies(project, context, projects()))
        } else {
            warn("Couldn't find a runner for project ${project.name}")
            return TaskResult()
        }
    }

    private fun projects() = context.pluginInfo.projectContributors.flatMap { it.projects() }

    private fun isFatJar(packages: List<PackageConfig>, jarName: String): Boolean {
        packages.forEach { pc ->
            pc.jars.forEach { jar ->
                if ((jar.name == null || jar.name == jarName) && jar.fatJar) {
                    return true
                }
            }
        }
        return false
    }

    // IRunContributor

    override fun affinity(project: Project, context: KobaltContext): Int {
        return if (configurationFor(project) != null) IAffinity.DEFAULT_POSITIVE_AFFINITY else 0
    }

    override fun run(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>): TaskResult {
        var result = TaskResult()
        configurationFor(project)?.let { config ->
            if (config.mainClass != null) {
                result = runJarFile(project, config)
            } else {
                throw KobaltException("No \"mainClass\" specified in the application{} part of project ${project.name}")
            }
        }
        return result
    }

    private fun runJarFile(project: Project, config: ApplicationConfig) : TaskResult {
        val jarName = project.projectProperties.get(PackagingPlugin.JAR_NAME) as String
        @Suppress("UNCHECKED_CAST")
        val packages = project.projectProperties.get(PackagingPlugin.PACKAGES) as List<PackageConfig>
        val allDeps = arrayListOf(jarName)
        val java = JavaInfo.create(File(SystemProperties.javaBase)).javaExecutable!!
        if (! isFatJar(packages, jarName)) {
            @Suppress("UNCHECKED_CAST")
            val projDeps = project.projectProperties.get(JvmCompilerPlugin.DEPENDENT_PROJECTS)
                    as List<ProjectDescription>
            // If the jar file is not fat, we need to add the transitive closure of all dependencies
            // on the classpath
            val allTheDependencies =
                    dependencyManager.calculateDependencies(project, context, projDeps,
                            allDependencies = project.compileDependencies).map { it.jarFile.get().path }
            allDeps.addAll(allTheDependencies)
        }
        val allDepsJoined = allDeps.joinToString(File.pathSeparator)
        val args = listOf("-classpath", allDepsJoined) + config.jvmArgs + config.mainClass!!
        val exitCode = RunCommand(java.absolutePath).run(args,
                successCallback = { output: List<String> ->
                    println(output.joinToString("\n"))
                },
                errorCallback =  { output: List<String> ->
                    println("ERROR")
                    println(output.joinToString("\n"))
                }
        )
        return TaskResult(exitCode == 0)
    }

    //ITaskContributor
    override fun tasksFor(context: KobaltContext): List<DynamicTask> = taskContributor.dynamicTasks
}

