package com.beust.kobalt.plugin.apt

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import javax.inject.Singleton

/**
 * The AptPlugin has two components:
 * 1) A new apt directive inside a dependency{} block (similar to compile()) that declares where
 * the annotation process is found
 * 2) An apt{} configuration on Project that lets the user configure how the annotation is performed
 * (outputDir, etc...).
 */
@Singleton
public class AptPlugin @Inject constructor(val depFactory: DepFactory, val executors: KobaltExecutors)
        : ConfigPlugin<AptConfig>(), ICompilerFlagContributor {
    companion object {
        const val TASK_APT: String = "runApt"
        const val NAME = "apt"
    }

    override val name = NAME

    // ICompilerFlagContributor
    override fun flagsFor(project: Project) : List<String> {
        val result = arrayListOf<String>()
        configurationFor(project)?.let { config ->
            aptDependencies.get(key = project.name)?.let { aptDependency ->
                val dependencyJarFile = JarFinder.byId(aptDependency)
                result.add("-processorpath")
                result.add(dependencyJarFile.absolutePath)
                val generated = KFiles.joinAndMakeDir(project.directory, project.buildDirectory!!, config.outputDir)
                result.add("-s")
                result.add(generated)
            }
            log(2, "New flags from apt: " + result.joinToString(" "))
        }
        return result
    }

    private val aptDependencies = hashMapOf<String, String>()

    fun addAptDependency(dependencies: Dependencies, it: String) {
        aptDependencies.put(dependencies.project.name, it)
    }
}

class AptConfig(var outputDir: String = "generated/sources/apt")

@Directive
public fun Project.apt(init: AptConfig.() -> Unit) {
    AptConfig().let {
        it.init()
        (Kobalt.findPlugin(AptPlugin.NAME) as AptPlugin).addConfiguration(this, it)
    }
}

@Directive
fun Dependencies.apt(vararg dep: String) {
    dep.forEach {
        (Kobalt.findPlugin(AptPlugin.NAME) as AptPlugin).addAptDependency(this, it)
    }
}
