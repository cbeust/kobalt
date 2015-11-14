package com.beust.kobalt.plugin.application

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.Plugins
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.TaskResult
import com.beust.kobalt.maven.KobaltException
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.RunCommand
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
        (Plugins.findPlugin(ApplicationPlugin.NAME) as ApplicationPlugin).addConfig(this, config)
    }
}

@Singleton
class ApplicationPlugin @Inject constructor(val executors: KobaltExecutors) : BasePlugin() {

    companion object {
        const val NAME = "application"
    }

    override val name = NAME

    val configs = hashMapOf<String, ApplicationConfig>()

    fun addConfig(project: Project, config: ApplicationConfig) {
        configs.put(project.name, config)
    }

    @Task(name = "run", description = "Run the main class", runAfter = arrayOf("assemble"))
    fun taskRun(project: Project): TaskResult {
        configs[project.name]?.let { config ->
            val java = JavaInfo.create(File(SystemProperties.javaBase)).javaExecutable!!
            if (config.mainClass != null) {
                val jarName = context.pluginProperties.get("packaging", PackagingPlugin.JAR_NAME) as String
                val args = listOf("-classpath", jarName) + config.jvmArgs + config.mainClass!!
                RunCommand(java.absolutePath).run(args, successCallback = { output: List<String> ->
                    println(output.joinToString("\n"))
                })
            } else {
                throw KobaltException("No \"mainClass\" specified in the application{} part of project ${project.name}")
            }
        }
        return TaskResult()
    }
}

