package com.beust.kobalt.plugin.apt

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.warn
import com.google.common.collect.ArrayListMultimap
import com.google.inject.Inject
import java.io.File
import java.util.*
import javax.inject.Singleton

/**
 * The AptPlugin has two components:
 * 1) A new apt directive inside a dependency{} block (similar to compile()) that declares where
 * the annotation processor is found
 * 2) An apt{} configuration on Project that lets the user configure how the annotation is performed
 * (outputDir, etc...).
 */
@Singleton
class AptPlugin @Inject constructor(val dependencyManager: DependencyManager)
    : BasePlugin(), ICompilerFlagContributor, ISourceDirectoryContributor {

    // ISourceDirectoryContributor

    private fun generatedDir(project: Project, outputDir: String) : File
        = File(KFiles.joinDir(project.directory, KFiles.KOBALT_BUILD_DIR, outputDir))

    override fun sourceDirectoriesFor(project: Project, context: KobaltContext): List<File> {
        val result = arrayListOf<File>()
        aptConfigs[project.name]?.let { config ->
            result.add(generatedDir(project, config.outputDir))
        }

        kaptConfigs[project.name]?.let { config ->
            result.add(generatedDir(project, config.outputDir))
        }

        return result
    }

    companion object {
        const val PLUGIN_NAME = "Apt"
        const val KAPT_CONFIG = "kaptConfig"
        const val APT_CONFIG = "aptConfig"
    }

    override val name = PLUGIN_NAME

    override fun apply(project: Project, context: KobaltContext) {
        listOf(aptConfigs[project.name]?.outputDir, aptConfigs[project.name]?.outputDir)
            .filterNotNull()
            .map { generatedDir(project, it) }
            .forEach {
                context.logger.log(project.name, 1, "Deleting " + it.absolutePath)
                val success = it.deleteRecursively()
                if (! success) warn("  Couldn't delete " + it.absolutePath)
            }
    }

    private fun generated(project: Project, context: KobaltContext, outputDir: String) =
            KFiles.joinAndMakeDir(project.directory, project.buildDirectory, outputDir,
                    context.variant.toIntermediateDir())

    // ICompilerFlagContributor
    override fun compilerFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>): List<String> {
        if (!suffixesBeingCompiled.contains("java")) return emptyList()

        val result = arrayListOf<String>()

        fun addFlags(outputDir: String) {
            aptDependencies[project.name]?.let {
                result.add("-s")
                result.add(generated(project, context, outputDir))
            }
        }

        aptConfigs[project.name]?.let { config ->
            addFlags(config.outputDir)
        }

        kaptConfigs[project.name]?.let { config ->
            addFlags(config.outputDir)
        }

        context.logger.log(project.name, 2, "New flags from apt: " + result.joinToString(" "))
        return result
    }

    private val aptDependencies = ArrayListMultimap.create<String, String>()

    fun addAptDependency(dependencies: Dependencies, it: String) {
        aptDependencies.put(dependencies.project.name, it)
    }

    private val aptConfigs: HashMap<String, AptConfig> = hashMapOf()
    private val kaptConfigs: HashMap<String, KaptConfig> = hashMapOf()

    fun addAptConfig(project: Project, kapt: AptConfig) {
        project.projectProperties.put(APT_CONFIG, kapt)
        aptConfigs.put(project.name, kapt)
    }

    fun addKaptConfig(project: Project, kapt: KaptConfig) {
        project.projectProperties.put(KAPT_CONFIG, kapt)
        kaptConfigs.put(project.name, kapt)
    }
}

class AptConfig(var outputDir: String = "generated/source/apt")

@Directive
fun Project.apt(init: AptConfig.() -> Unit) {
    AptConfig().let {
        it.init()
        (Kobalt.findPlugin(AptPlugin.PLUGIN_NAME) as AptPlugin).addAptConfig(this, it)
    }
}

@Directive
fun Dependencies.apt(vararg dep: String) {
    dep.forEach {
        (Kobalt.findPlugin(AptPlugin.PLUGIN_NAME) as AptPlugin).addAptDependency(this, it)
    }
}

class KaptConfig(var outputDir: String = "generated/source/apt")

@Directive
fun Project.kapt(init: KaptConfig.() -> Unit) {
    KaptConfig().let {
        it.init()
        (Kobalt.findPlugin(AptPlugin.PLUGIN_NAME) as AptPlugin).addKaptConfig(this, it)
    }
}
