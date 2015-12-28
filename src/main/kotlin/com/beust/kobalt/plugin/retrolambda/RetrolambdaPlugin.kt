package com.beust.kobalt.plugin.retrolambda

import com.beust.kobalt.Plugins
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.RunCommand
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.nio.charset.Charset

/**
 * Run Retrolambda on the classes right after "compile". This plug-in automatically downloads and uses
 * the most recent retrolambda.jar and it can be configured with the `retrolambda{}` directive.
 */
@Singleton
class RetrolambdaPlugin @Inject constructor(val dependencyManager: DependencyManager,
                                            val taskContributor: TaskContributor)
: ConfigPlugin<RetrolambdaConfig>(), IClasspathContributor, ITaskContributor {

    override val name = PLUGIN_NAME

    companion object {
        const val PLUGIN_NAME = "Retrolambda"
        // Note the use of the versionless id here (no version number specified, ends with ":") so that
        // we will always be using the latest version of the Retrolambda jar
        const val ID = "net.orfjackal.retrolambda:retrolambda:"
        val JAR = MavenDependency.create(ID)
    }

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        taskContributor.addVariantTasks(this, project, context, "retrolambda", runTask = { taskRetrolambda(project) },
                runBefore = listOf("generateDex"), alwaysRunAfter = listOf("compile"))
    }

    // IClasspathContributor
    // Only add the Retrolambda jar file if the user specified a `retrolambda{}` directive in their build file
    override fun entriesFor(project: Project?) =
            if (project != null && configurationFor(project) != null) listOf(JAR)
            else emptyList()

    @Task(name = "retrolambda", description = "Run Retrolambda", runBefore = arrayOf("generateDex"),
            alwaysRunAfter = arrayOf(JvmCompilerPlugin.TASK_COMPILE))
    fun taskRetrolambda(project: Project): TaskResult {
        val config = configurationFor(project)
        val result =
                if (config != null) {
                    val classesDir = project.classesDir(context)
                    val classpath = (dependencyManager.calculateDependencies(project, context, projects,
                            project.compileDependencies)
                            .map {
                                it.jarFile.get()
                            } + classesDir).joinToString("\n")

                    // Use retrolambda.classpathFile instead of retrolambda.classpath to avoid problems
                    // with file path separators on Windows
                    val classpathFile = File.createTempFile("kobalt-", "")
                    classpathFile.writeText(classpath, Charset.defaultCharset())

                    val args = listOf(
                            "-Dretrolambda.inputDir=" + classesDir.replace("\\", "/"),
                            "-Dretrolambda.classpathFile=" + classpathFile,
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

    // ITaskContributor
    override fun tasksFor(context: KobaltContext): List<DynamicTask> = taskContributor.dynamicTasks
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
