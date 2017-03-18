package com.beust.kobalt.plugin.groovy

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.BaseJvmPlugin
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroovyPlugin @Inject constructor(val groovyCompiler: GroovyCompiler,
        override val configActor: ConfigActor<GroovyConfig>)
    : BaseJvmPlugin<GroovyConfig>(configActor), ICompilerContributor, ISourceDirectoryContributor {

    companion object {
        val PLUGIN_NAME = "Groovy"
        val SOURCE_SUFFIXES = listOf("groovy")
    }

    override val name = PLUGIN_NAME

    override fun sourceSuffixes() = SOURCE_SUFFIXES

    // ICompilerFlagsContributor
    override fun compilerFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>) =
            maybeCompilerArgs(compiler.sourceSuffixes, suffixesBeingCompiled,
                    configurationFor(project)?.compilerArgs ?: listOf<String>())

    // ICompilerContributor
    val compiler = CompilerDescription(PLUGIN_NAME, "groovy", SOURCE_SUFFIXES, groovyCompiler)

    // ISourceDirectoryContributor
    override fun sourceDirectoriesFor(project: Project, context: KobaltContext)
            = listOf(File("src/main/groovy", "src/test/groovy"))

    override fun compilersFor(project: Project, context: KobaltContext)
            = if (sourceFileCount(project) > 0) listOf(compiler) else emptyList()
}

@Directive
fun Project.groovyCompiler(init: GroovyConfig.() -> Unit) =
    GroovyConfig(this).also { config ->
        config.init()
        (Kobalt.findPlugin(GroovyPlugin.PLUGIN_NAME) as GroovyPlugin).addConfiguration(this, config)
    }
