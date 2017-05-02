package com.beust.kobalt.plugin.java

import com.beust.kobalt.TaskResult
import com.beust.kobalt.Variant
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.BaseJvmPlugin
import com.beust.kobalt.misc.warn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaPlugin @Inject constructor(val javaCompiler: JavaCompiler, override val configActor: ConfigActor<JavaConfig>)
        : BaseJvmPlugin<JavaConfig>(configActor), IDocContributor, ICompilerContributor,
            ITestSourceDirectoryContributor, IBuildConfigContributor, IDocFlagContributor {

    companion object {
        val PLUGIN_NAME = "Java"
        val SOURCE_SUFFIXES = listOf("java")
    }

    override val name = PLUGIN_NAME

    override fun sourceSuffixes() = SOURCE_SUFFIXES

    override fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val result =
            if (info.sourceFiles.isNotEmpty()) {
                javaCompiler.javadoc(project, context, info)
            } else {
                warn("Couldn't find any source files to run Javadoc on for suffixes " + info.suffixesBeingCompiled)
                TaskResult()
            }
        return result
    }

    // ICompilerFlagsContributor
    override fun compilerFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>) =
                maybeCompilerArgs(compiler.sourceSuffixes, suffixesBeingCompiled,
                        configurationFor(project)?.compilerArgs ?: listOf<String>())

    // IDocFlagContributor
    override fun docFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>): List<String> {
        val config = javadocConfigurations[project.name]
        return if (config == null || config.args.isEmpty()) DEFAULT_JAVADOC_ARGS
            else config.args
    }

    val DEFAULT_JAVADOC_ARGS = listOf("-d", "javadoc", "-Xdoclint:none", "-Xmaxerrs", "1", "-quiet")

    val javadocConfigurations = hashMapOf<String, JavadocConfig>()

    fun addJavadocConfiguration(project: Project, configuration: JavadocConfig)
            = javadocConfigurations.put(project.name, configuration)

    // ICompilerContributor
    val compiler = CompilerDescription(PLUGIN_NAME, "java", SOURCE_SUFFIXES, javaCompiler)

    override fun compilersFor(project: Project, context: KobaltContext)
            = if (sourceFileCount(project) > 0) listOf(compiler) else emptyList()

    // ITestSourceDirectoryContributor
    override fun testSourceDirectoriesFor(project: Project, context: KobaltContext)
        = project.sourceDirectoriesTest.map { File(it) }.toList()

    override val buildConfigSuffix = compiler.sourceSuffixes[0]

    override fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String,
            variant: Variant, buildConfigs: List<BuildConfig>): String {
        return JavaBuildConfig().generateBuildConfig(project, context, packageName, variant, buildConfigs)
    }


}

class JavaConfig(val project: Project) {
    val compilerArgs = arrayListOf<String>()
    fun args(vararg options: String) = compilerArgs.addAll(options)
}

@Directive
fun Project.javaCompiler(init: JavaConfig.() -> Unit) =
    JavaConfig(this).also { config ->
        config.init()
        (Kobalt.findPlugin(JavaPlugin.PLUGIN_NAME) as JavaPlugin).addConfiguration(this, config)
    }

class JavadocConfig(val project: Project) {
    val args = arrayListOf<String>()
    fun args(vararg options: String) = args.addAll(options)
}

@Directive
fun Project.javadoc(init: JavadocConfig.() -> Unit) =
    JavadocConfig(this).also { config ->
        config.init()
        (Kobalt.findPlugin(JavaPlugin.PLUGIN_NAME) as JavaPlugin).addJavadocConfiguration(this, config)
    }

