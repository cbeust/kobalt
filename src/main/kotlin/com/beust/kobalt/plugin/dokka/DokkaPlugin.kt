package com.beust.kobalt.plugin.dokka

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.ConfigPlugin
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.misc.RunCommand
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
        val dokkaJar = depFactory.create(DOKKA_ID, context.executors.miscExecutor).jarFile.get().absolutePath
        if (config != null) {
            val args = listOf(
                    "-classpath", classpathString,
                    "-jar", dokkaJar,
                    "src/main/kotlin") +
                config.args
            RunCommand(javaExecutable.absolutePath).run(args, successCallback = {
                println("COMMAND SUCCESS")
            })
        } else {
            log(2, "No dokka configuration found for project ${project.name}, skipping it")
        }
        return TaskResult()
    }
}

/*
output - the output directory where the documentation is generated
format - the output format:
html - HTML (default)
markdown - Markdown
jekyll - Markdown adapted for Jekyll sites
javadoc - Javadoc (showing how the project can be accessed from Java)
classpath - list of directories or .jar files to include in the classpath (used for resolving references)
samples - list of directories containing sample code (documentation for those directories is not generated but declarations from them can be referenced using the @sample tag)
module - the name of the module being documented (used as the root directory of the generated documentation)
include - names of files containing the documentation for the module and individual packages
nodeprecated
 */
class DokkaConfig() {
    val args = arrayListOf<String>()
    fun args(vararg options: String) {
        args.addAll(options)
    }
}

@Directive
public fun Project.dokka(init: DokkaConfig.() -> Unit) = let { project ->
    with(DokkaConfig()) {
        init()
        (Kobalt.findPlugin(DokkaPlugin.PLUGIN_NAME) as DokkaPlugin).addConfiguration(project, this)
    }
}

