package com.beust.kobalt.plugin.apt

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.common.collect.ArrayListMultimap
import com.google.inject.Inject
import java.io.File
import javax.inject.Singleton

/**
 * The AptPlugin has two components:
 * 1) A new apt directive inside a dependency{} block (similar to compile()) that declares where
 * the annotation processor is found
 * 2) An apt{} configuration on Project that lets the user configure how the annotation is performed
 * (outputDir, etc...).
 */
@Singleton
class AptPlugin @Inject constructor(val dependencyManager: DependencyManager, val configActor: ConfigActor<AptConfig>)
    : BasePlugin(), ICompilerFlagContributor, ISourceDirectoryContributor, IConfigActor<AptConfig> by configActor {

    // ISourceDirectoryContributor

    override fun sourceDirectoriesFor(project: Project, context: KobaltContext): List<File> {
        val config = configurationFor(project)
        val result =
            if (config != null) {
                listOf(File(
                        KFiles.joinDir(KFiles.KOBALT_BUILD_DIR, config.outputDir, context.variant.toIntermediateDir())))
            } else {
                emptyList()
            }

        return result
    }

    companion object {
        const val PLUGIN_NAME = "Apt"
    }

    override val name = PLUGIN_NAME

    private fun generated(project: Project, context: KobaltContext, outputDir: String) =
            KFiles.joinAndMakeDir(project.directory, project.buildDirectory, outputDir,
                    context.variant.toIntermediateDir())

    // ICompilerFlagContributor
    override fun flagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>): List<String> {
        if (!suffixesBeingCompiled.contains("java")) return emptyList()

        val result = arrayListOf<String>()
        configurationFor(project)?.let { config ->
            aptDependencies[project.name]?.let { aptDependencies ->
                val deps = aptDependencies.map { dependencyManager.create(it) }

                val dependencies = context.dependencyManager.calculateDependencies(null, context, emptyList(), deps)
                        .map { it.jarFile.get().path }

                result.add("-processorpath")
                result.add((dependencies).joinToString(":"))
                result.add("-s")
                result.add(generated(project, context, config.outputDir))
            }
            log(2, "New flags from apt: " + result.joinToString(" "))
        }
        return result
    }

    private val aptDependencies = ArrayListMultimap.create<String, String>()

    fun addAptDependency(dependencies: Dependencies, it: String) {
        aptDependencies.put(dependencies.project.name, it)
    }
}

class AptConfig(var outputDir: String = "generated/source/apt")

@Directive
public fun Project.apt(init: AptConfig.() -> Unit) {
    AptConfig().let {
        it.init()
        (Kobalt.findPlugin(AptPlugin.PLUGIN_NAME) as AptPlugin).addConfiguration(this, it)
    }
}

@Directive
fun Dependencies.apt(vararg dep: String) {
    dep.forEach {
        (Kobalt.findPlugin(AptPlugin.PLUGIN_NAME) as AptPlugin).addAptDependency(this, it)
    }
}
