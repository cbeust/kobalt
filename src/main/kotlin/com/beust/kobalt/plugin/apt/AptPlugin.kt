package com.beust.kobalt.plugin.apt

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.common.collect.ArrayListMultimap
import com.google.inject.Inject
import javax.inject.Singleton

/**
 * The AptPlugin has two components:
 * 1) A new apt directive inside a dependency{} block (similar to compile()) that declares where
 * the annotation processor is found
 * 2) An apt{} configuration on Project that lets the user configure how the annotation is performed
 * (outputDir, etc...).
 */
@Singleton
public class AptPlugin @Inject constructor(val depFactory: DepFactory)
        : ConfigPlugin<AptConfig>(), ICompilerFlagContributor {

    companion object {
        const val PLUGIN_NAME = "Apt"
    }

    override val name = PLUGIN_NAME

    private fun generated(project: Project, context: KobaltContext, outputDir: String) =
            KFiles.joinAndMakeDir(project.directory, project.buildDirectory, outputDir,
                    context.variant.toIntermediateDir())

    // ICompilerFlagContributor
    override fun flagsFor(project: Project, context: KobaltContext, currentFlags: List<String>) : List<String> {
        val result = arrayListOf<String>()
        configurationFor(project)?.let { config ->
            aptDependencies[project.name]?.let { aptDependencies ->
                val dependencyJarFiles = aptDependencies.map {
                        JarFinder.byId(it)
                    }.map {
                        it.absolutePath
                    }
                val deps = aptDependencies.map { depFactory.create(it) }

                val dependencies = context.dependencyManager.calculateDependencies(null, context, emptyList(),
                        deps).map { it.jarFile.get().path }

//                result.add("-Xbootclasspath/a:" + dependencies)

                result.add("-processorpath")
                result.add((dependencyJarFiles + dependencies).joinToString(":"))
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
