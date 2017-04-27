package com.beust.kobalt.plugin.application

import com.beust.kobalt.Jvm
import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.archive.Archives
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.runCommand
import com.beust.kobalt.plugin.packaging.PackageConfig
import com.beust.kobalt.plugin.packaging.PackagingPlugin
import com.google.inject.Inject
import com.google.inject.Singleton
import org.jetbrains.kotlin.config.TargetPlatformVersion.NoVersion.description
import java.io.File

class ApplicationConfig {
    @Directive
    var taskName: String = "run"

    @Directive
    var mainClass: String? = null

    @Directive
    fun jvmArgs(vararg args: String) = args.forEach { jvmArgs.add(it) }
    val jvmArgs = arrayListOf<String>()

    @Directive
    fun args(vararg argv: String) = argv.forEach { args.add(it) }
    val args = arrayListOf<String>()
}

@Directive
fun Project.application(init: ApplicationConfig.() -> Unit): ApplicationConfig {
    return ApplicationConfig().also { config ->
        config.init()
        (Plugins.findPlugin(ApplicationPlugin.PLUGIN_NAME) as ApplicationPlugin).addConfiguration(this, config)
    }
}

@Singleton
class ApplicationPlugin @Inject constructor(val configActor: ConfigsActor<ApplicationConfig>,
        val executors: KobaltExecutors, val nativeManager: NativeManager,
        val dependencyManager: DependencyManager, val taskContributor : TaskContributor, val jvm: Jvm)
            : BasePlugin(), ITaskContributor, IConfigsActor<ApplicationConfig> by configActor {

    companion object {
        const val PLUGIN_NAME = "Application"
    }

    override val name = PLUGIN_NAME

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)

        configurationFor(project)?.let { configs ->
            configs.forEach { config ->
                taskContributor.addTask(this, project, config.taskName,
                        description = "Run the class " + config.mainClass,
                        group = "run",
                        dependsOn = listOf("assemble"),
                        runTask = { run(project, context, config) })
            }
        }
    }

//    fun taskRun(project: Project, config: ApplicationConfig): TaskResult {
//        val runContributor = ActorUtils.selectAffinityActor(project, context,
//                context.pluginInfo.runnerContributors)
//        if (runContributor != null && runContributor.affinity(project, context) > 0) {
//            return runContributor.run(project, context,
//                    dependencyManager.dependencies(project, context, listOf(Scope.RUNTIME)))
//        } else {
//            context.logger.log(project.name, 1,
//                    "Couldn't find a runner for project ${project.name}. Please make sure" +
//                    " your build file contains " +
//                    "an application{} directive with a mainClass=... in it")
//            return TaskResult()
//        }
//    }

    private fun isFatJar(packages: List<PackageConfig>, jarName: String): Boolean {
        val foundJar = packages.flatMap { it.jars }.filter { jarName.endsWith(it.name) }
        return foundJar.size == 1 && foundJar[0].fatJar
    }

    private fun run(project: Project, context: KobaltContext, config: ApplicationConfig): TaskResult {
        if (project.nativeDependencies.any()) {
            nativeManager.installLibraries(project)
        }

        val result =
            if (config.mainClass != null) {
                runJarFile(project, context, config)
            } else {
                throw KobaltException("No \"mainClass\" specified in the application{} part of project ${project.name}")
            }

        return result
    }

    private fun runJarFile(project: Project, context: KobaltContext, config: ApplicationConfig) : TaskResult {
        // If the user specified a Main-Class attribute when creating their jar file manifest, use that. Otherwise,
        // use the default jar file name and hope there's a main class in it
        val fileName = project.projectProperties.get(Archives.JAR_NAME_WITH_MAIN_CLASS)?.toString()
            ?: project.projectProperties.get(Archives.JAR_NAME)?.toString()
            ?: throw KobaltException("Couldn't find any jar file with a main class in it")

        // The application will run in the project's directory, so we don't need to add project.directory here
        val jarName = KFiles.joinDir(project.buildDirectory, KFiles.LIBS_DIR, fileName)
        @Suppress("UNCHECKED_CAST")
        val packages = project.projectProperties.get(PackagingPlugin.PACKAGES) as List<PackageConfig>
        val allDeps = arrayListOf(jarName)
        val java = jvm.javaExecutable!!
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
        val allArgs = contributorFlags + initialArgs + config.args
        val exitCode = runCommand {
            command = "java"
            args = allArgs
            directory = File(project.directory)
            successCallback = { output: List<String> ->
                kobaltLog(1, output.joinToString("\n"))
            }
            errorCallback =  { output: List<String> ->
                kobaltLog(1, "ERROR")
                kobaltLog(1, output.joinToString("\n"))
            }
        }
        return TaskResult(exitCode == 0)
    }

    //ITaskContributor
    override fun tasksFor(project: Project, context: KobaltContext): List<DynamicTask> {
        val result = arrayListOf<DynamicTask>()
        configurationFor(project)?.let { configs ->
            configs.forEach { config ->
                result.add(DynamicTask(this, config.taskName, "Run the class " + config.mainClass, "run", project,
                        dependsOn = listOf("assemble"),
                        closure = { run(project, context, config) }))
            }
        }

        return result
    }
}

