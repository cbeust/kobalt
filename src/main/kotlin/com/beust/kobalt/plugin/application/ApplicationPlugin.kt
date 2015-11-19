package com.beust.kobalt.plugin.application

import com.beust.kobalt.*
import com.beust.kobalt.api.ConfigPlugin
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.ProjectDescription
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.RunCommand
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
        (Plugins.findPlugin(ApplicationPlugin.NAME) as ApplicationPlugin).addConfiguration(this, config)
    }
}

@Singleton
class ApplicationPlugin @Inject constructor(val executors: KobaltExecutors,
        val dependencyManager: DependencyManager) : ConfigPlugin<ApplicationConfig>() {

    companion object {
        const val NAME = "application"
    }

    override val name = NAME

    @Task(name = "run", description = "Run the main class", runAfter = arrayOf("assemble"))
    fun taskRun(project: Project): TaskResult {
        var result = TaskResult()
        configurationFor(project)?.let { config ->
            val java = JavaInfo.create(File(SystemProperties.javaBase)).javaExecutable!!
            if (config.mainClass != null) {
                val jarName = project.projectProperties.get(PackagingPlugin.JAR_NAME) as String
                val packages = project.projectProperties.get(PackagingPlugin.PACKAGES) as List<PackageConfig>
                val allDeps = arrayListOf(jarName)
                if (! isFatJar(packages, jarName)) {
                    val projDeps = project.projectProperties.get(JvmCompilerPlugin.DEPENDENT_PROJECTS)
                        as List<ProjectDescription>
                    // If the jar file is not fat, we need to add the transitive closure of all dependencies
                    // on the classpath
                    allDeps.addAll(
                            dependencyManager.calculateDependencies(project, context, projDeps,
                                    allDependencies = project.compileDependencies).map { it.jarFile.get().path })
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
                result = TaskResult(exitCode == 0)
            } else {
                throw KobaltException("No \"mainClass\" specified in the application{} part of project ${project.name}")
            }
        }
        return result
    }

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
}

