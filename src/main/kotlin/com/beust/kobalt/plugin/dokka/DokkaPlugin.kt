package com.beust.kobalt.plugin.dokka

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.ConfigPlugin
import com.beust.kobalt.api.JarFinder
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.misc.RunCommand
import com.beust.kobalt.misc.error
import com.beust.kobalt.misc.log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DokkaPlugin @Inject constructor(val depFactory: DepFactory) : ConfigPlugin<DokkaConfig>() {
    override val name = PLUGIN_NAME

    companion object {
        const val PLUGIN_NAME = "dokka"
        const val DOKKA_ID = "org.jetbrains.dokka:dokka-fatjar:0.9.1"
    }

    /**
     * Probably no point in running this task if "assemble" hasn't completed.
     */
    @Task(name = "dokka", description = "Run dokka",
            runBefore = arrayOf("compile")
//            runAfter = arrayOf(PackagingPlugin.TASK_ASSEMBLE)
    )
    fun taskDokka(project: Project) : TaskResult {
        val javaExecutable = JavaInfo.create(File(SystemProperties.javaBase)).javaExecutable!!
        val config = configurationFor(project)
        val classpath = context.dependencyManager.calculateDependencies(project, context)
        val buildDir = project.projectProperties.getString(JvmCompilerPlugin.BUILD_DIR)
        val classpathString = (classpath.map { it.jarFile.get().absolutePath } +
                listOf(buildDir))
            .joinToString(File.pathSeparator)
        val dokkaJar = JarFinder.byId(DOKKA_ID)
        var success = true
        if (config != null) {
            val args : List<String> = listOf(
                    "-classpath", classpathString,
                    "-jar", dokkaJar.absolutePath,
                    *(project.sourceDirectories.toTypedArray())) +
                config.args
            RunCommand(javaExecutable.absolutePath).run(args, errorCallback = { output: List<String> ->
                error("Error running dokka:\n " + output.joinToString("\n"))
                success = false
            })
        } else {
            log(2, "No dokka configuration found for project ${project.name}, skipping it")
        }
        return TaskResult(success)
    }
}

class DokkaConfig() {
    val args = arrayListOf<String>()
    fun args(vararg options: String) {
        args.addAll(options)
    }

    var linkMapping: LinkMappingConfig? = null

    @Directive
    fun linkMapping(init: LinkMappingConfig.() -> Unit) {
        linkMapping = LinkMappingConfig().let {
            it.init()
            it
        }
    }
}

class LinkMappingConfig {
    var dir: String = ""
    var url: String = ""
    var suffix: String? = null
}

@Directive
public fun Project.dokka(init: DokkaConfig.() -> Unit) = let { project ->
    with(DokkaConfig()) {
        init()
        (Kobalt.findPlugin(DokkaPlugin.PLUGIN_NAME) as DokkaPlugin).addConfiguration(project, this)
    }
}

