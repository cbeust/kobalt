package com.beust.kobalt.plugin.java

import com.beust.kobalt.TaskResult
import com.beust.kobalt.Variant
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.BaseJvmPlugin
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.misc.warn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaPlugin @Inject constructor(val javaCompiler: JavaCompiler)
        : BaseJvmPlugin<JavaConfig>(), IDocContributor, ICompilerContributor, ITestSourceDirectoryContributor,
            IBuildConfigContributor {
    companion object {
        const val PLUGIN_NAME = "Java"
    }

    override val name = PLUGIN_NAME

    override fun accept(project: Project) = project.projectExtra.suffixesFound.contains("java")

    // IDocContributor
    override fun affinity(project: Project, context: KobaltContext) =
            if (accept(project)) 1 else 0

    override fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val result =
            if (info.sourceFiles.size > 0) {
                javaCompiler.javadoc(project, context, info)
            } else {
                warn("Couldn't find any source files to run Javadoc on")
                TaskResult()
            }
        return result
    }

    // ICompilerFlagsContributor
    override fun flagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>) =
                maybeCompilerArgs(sourceSuffixes, suffixesBeingCompiled,
                        configurationFor(project)?.compilerArgs ?: listOf<String>())

    // ICompilerContributor
    override val sourceSuffixes = listOf("java")

    override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val result =
            if (info.sourceFiles.size > 0) {
                javaCompiler.compile(project, context, info)
            } else {
                warn("Couldn't find any source files to compile")
                TaskResult()
            }
        return result
    }

    // ITestSourceDirectoryContributor
    override fun testSourceDirectoriesFor(project: Project, context: KobaltContext)
        = project.sourceDirectoriesTest.map { File(it) }.toList()

    // IBuildConfigContributor
    override fun affinity(project: Project) = if (project.projectExtra.suffixesFound.contains("java")) 1 else 0

    override val buildConfigSuffix = sourceSuffixes[0]

    override fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String,
            variant: Variant, buildConfigs: List<BuildConfig>): String {
        return JavaBuildConfig().generateBuildConfig(project, context, packageName, variant, buildConfigs)
    }


}

@Directive
public fun javaProject(vararg projects: Project, init: Project.() -> Unit): Project {
    return Project().apply {
        warn("javaProject{} is deprecated, please use project{}")
        init()
        (Kobalt.findPlugin(JvmCompilerPlugin.PLUGIN_NAME) as JvmCompilerPlugin)
                .addDependentProjects(this, projects.toList())
    }
}

class JavaConfig(val project: Project) {
    val compilerArgs = arrayListOf<String>()
    fun args(vararg options: String) = compilerArgs.addAll(options)
}

@Directive
fun Project.javaCompiler(init: JavaConfig.() -> Unit) = let {
    val config = JavaConfig(it)
    config.init()
    (Kobalt.findPlugin(JavaPlugin.PLUGIN_NAME) as JavaPlugin).addConfiguration(this, config)
}
