package com.beust.kobalt.plugin.retrolambda

import com.beust.kobalt.Plugins
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.ConfigPlugin
import com.beust.kobalt.api.IClasspathContributor
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.RunCommand
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File

/**
 * Run Retrolambda on the classes right after "compile". This plug-in automatically downloads and uses
 * the most recent retrolambda.jar and it can be configured with the `retrolambda{}` directive.
 */
@Singleton
class RetrolambdaPlugin @Inject constructor(val dependencyManager: DependencyManager)
        : ConfigPlugin<RetrolambdaConfig>(), IClasspathContributor {

    override val name = PLUGIN_NAME

    companion object {
        const val PLUGIN_NAME = "Retrolambda"
        const val ID = "net.orfjackal.retrolambda:retrolambda:"
        val JAR = MavenDependency.create(ID)
    }

    // IClasspathContributor
    override fun entriesFor(project: Project?) =
            if (project != null && configurationFor(project) != null) listOf(JAR)
            else emptyList()

    @Task(name = "retrolambda", description = "Run Retrolambda",
            alwaysRunAfter = arrayOf(JvmCompilerPlugin.TASK_COMPILE))
    fun taskRetrolambda(project: Project): TaskResult {
        val config = configurationFor(project)
        val result = if (config != null) {
            val classpath = dependencyManager.transitiveClosure(project.compileDependencies).map {
                it.jarFile.get()
            }.joinToString(File.separator)

            val args = listOf(
                    "-Dretrolambda.inputDir=" + project.classesDir(context),
                    "-Dretrolambda.classpath=" + classpath,
                    "-Dretrolambda.bytecodeVersion=${config.byteCodeVersion}",
                    "-jar", JAR.jarFile.get().path)

            val result = RunCommand("java").apply {
                directory = File(project.directory)
            }.run(args)
            TaskResult(result == 0)
        } else {
            TaskResult()
        }

        return result
    }
}

class RetrolambdaConfig(var byteCodeVersion: Int = 50) {
}

@Directive
fun Project.retrolambda(init: RetrolambdaConfig.() -> Unit) = let {
    RetrolambdaConfig().apply {
        init()
        (Plugins.findPlugin(RetrolambdaPlugin.PLUGIN_NAME) as RetrolambdaPlugin).addConfiguration(it, this)
    }
}
