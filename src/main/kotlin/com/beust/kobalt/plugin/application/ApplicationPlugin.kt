package com.beust.kobalt.plugin.application

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.archive.Archives
import com.beust.kobalt.internal.ActorUtils
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.RunCommand
import com.beust.kobalt.plugin.packaging.PackageConfig
import com.beust.kobalt.plugin.packaging.PackagingPlugin
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File

@Directive
fun Project.application(init: ApplicationConfig.() -> Unit): ApplicationConfig {
    return ApplicationConfig().also { config ->
        config.init()
        (Plugins.findPlugin(ApplicationPlugin.PLUGIN_NAME) as ApplicationPlugin).addConfiguration(this, config)
    }
}

@Singleton
class ApplicationPlugin @Inject constructor(val configActor: ConfigActor<ApplicationConfig>,
                                            val executors: KobaltExecutors, val nativeManager: NativeManager,
                                            val dependencyManager: DependencyManager, val taskContributor : TaskContributor)
            : BasePlugin(), IRunnerContributor, ITaskContributor, IConfigActor<ApplicationConfig> by configActor {

    companion object {
        const val PLUGIN_NAME = "Application"
    }

    override val name = PLUGIN_NAME

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        taskContributor.addVariantTasks(this, project, context, "run", group = "run", dependsOn = listOf("install"),
                runTask = { taskRun(project) })
    }

    @Task(name = "run", description = "Run the main class", group = "run", dependsOn = arrayOf("install"))
    fun taskRun(project: Project): TaskResult {
        val runContributor = ActorUtils.selectAffinityActor(project, context,
                context.pluginInfo.runnerContributors)
        if (runContributor != null && runContributor.affinity(project, context) > 0) {
            return runContributor.run(project, context, dependencyManager.dependencies(project, context))
        } else {
            context.logger.log(project.name, 1,
                    "Couldn't find a runner for project ${project.name}. Please make sure" +
                    " your build file contains " +
                    "an application{} directive with a mainClass=... in it")
            return TaskResult()
        }
    }

    private fun isFatJar(packages: List<PackageConfig>, jarName: String): Boolean {
        val foundJar = packages.flatMap { it.jars }.filter { jarName.endsWith(it.name) }
        return foundJar.size == 1 && foundJar[0].fatJar
    }

    // IRunContributor

    override fun affinity(project: Project, context: KobaltContext): Int {
        return if (configurationFor(project) != null) IAffinity.DEFAULT_POSITIVE_AFFINITY else 0
    }

    override fun run(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>): TaskResult {
        var result = TaskResult()
        if (project.nativeDependencies.any()) {
            nativeManager.installLibraries(project)
        }
        configurationFor(project)?.let { config ->
            if (config.mainClass != null) {
                result = runJarFile(project, context, config)
            } else {
                throw KobaltException("No \"mainClass\" specified in the application{} part of project ${project.name}")
            }
        }
        return result
    }

    private fun runJarFile(project: Project, context: KobaltContext, config: ApplicationConfig) : TaskResult {
        val fileName = project.projectProperties.get(Archives.JAR_NAME) as String
        val jarFileName = KFiles.joinDir(KFiles.libsDir(project), fileName)
        val jarName = (jarFileName ?: KFiles.joinDir(KFiles.libsDir(project),
                context.variant.archiveName(project, null, ".jar")))
            as String
        @Suppress("UNCHECKED_CAST")
        val packages = project.projectProperties.get(PackagingPlugin.PACKAGES) as List<PackageConfig>
        val allDeps = arrayListOf(jarName)
        val java = JavaInfo.create(File(SystemProperties.javaBase)).javaExecutable!!
        if (! isFatJar(packages, jarName)) {
            @Suppress("UNCHECKED_CAST")
            // If the jar file is not fat, we need to add the transitive closure of all dependencies
            // on the classpath
            val allDependencies = project.compileDependencies + project.compileRuntimeDependencies
            val allTheDependencies =
                    dependencyManager.calculateDependencies(project, context,
                            scopes = listOf(Scope.COMPILE, Scope.RUNTIME),
                            passedDependencies = allDependencies)
                            .map { it.jarFile.get().path }
            allDeps.addAll(allTheDependencies)
        }
        val allDepsJoined = allDeps.joinToString(File.pathSeparator)
        val initialArgs = listOf("-classpath", allDepsJoined) + config.jvmArgs + config.mainClass!!
        val contributorFlags = context.pluginInfo.jvmFlagContributors.flatMap {
            it.jvmFlagsFor(project, context, initialArgs)
        }
        val args = contributorFlags + initialArgs + config.args
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
    override fun tasksFor(project: Project, context: KobaltContext): List<DynamicTask> = taskContributor.dynamicTasks
}

