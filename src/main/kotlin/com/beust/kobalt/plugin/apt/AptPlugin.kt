package com.beust.kobalt.plugin.apt

import com.beust.kobalt.Constants
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.CompilerUtils
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.Filters
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.warn
import com.beust.kobalt.plugin.kotlin.KotlinPlugin
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
class AptPlugin @Inject constructor(val dependencyManager: DependencyManager, val kotlinPlugin: KotlinPlugin,
        val compilerUtils: CompilerUtils)
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
        super.apply(project, context)
        listOf(aptConfigs[project.name]?.outputDir, aptConfigs[project.name]?.outputDir)
            .filterNotNull()
            .distinct()
            .map { generatedDir(project, it) }
            .forEach {
                it.normalize().absolutePath.let { path ->
                    context.logger.log(project.name, 1, "  Deleting " + path)
                    val success = it.deleteRecursively()
                    if (!success) warn("  Couldn't delete " + path)
                }
            }
    }

    private fun generated(project: Project, context: KobaltContext, outputDir: String) =
            KFiles.joinAndMakeDir(project.directory, project.buildDirectory, outputDir,
                    context.variant.toIntermediateDir())

    private fun generatedSources(project: Project, context: KobaltContext, outputDir: String) =
            KFiles.joinDir(generated(project, context, outputDir), "sources")
    private fun generatedStubs(project: Project, context: KobaltContext, outputDir: String) =
            KFiles.joinDir(generated(project, context, outputDir), "stubs")
    private fun generatedClasses(project: Project, context: KobaltContext, outputDir: String) =
            KFiles.joinDir(generated(project, context, outputDir), "classes")

//    @Task(name = "compileKapt", dependsOn = arrayOf("runKapt"), reverseDependsOn = arrayOf("compile"))
    fun taskCompileKapt(project: Project) : TaskResult {
        kaptConfigs[project.name]?.let { config ->
            val sourceDirs = listOf(
                    generatedStubs(project, context, config.outputDir),
                    generatedSources(project, context, config.outputDir))
            val sourceFiles = KFiles.findSourceFiles(project.directory, sourceDirs, listOf("kt", "java")).toList()
            val buildDirectory = File(KFiles.joinDir(project.directory,
                    generatedClasses(project, context, config.outputDir)))
            val flags = listOf<String>()
            val cai = CompilerActionInfo(project.directory, allDependencies(), sourceFiles, listOf(".kt"),
                    buildDirectory, flags, emptyList(), forceRecompile = true)

            val cr = compilerUtils.invokeCompiler(project, context, kotlinPlugin.compiler, cai)
            println("")
        }

        return TaskResult()
    }

    val annotationDependencyId = "org.jetbrains.kotlin:kotlin-annotation-processing:" +
            Constants.KOTLIN_COMPILER_VERSION

    fun annotationProcessorDependency() = dependencyManager.create(annotationDependencyId)

    fun aptJarDependencies() : List<IClasspathDependency> {
        val apDep = dependencyManager.create("net.thauvin.erik.:semver:0.9.6-beta")
        val apDep2 = FileDependency(homeDir("t/semver-example-kotlin/lib/semver-0.9.7.jar"))
        return listOf(apDep2)
    }

    fun allDependencies(): List<IClasspathDependency> {
        val allDeps = listOf(annotationProcessorDependency()) + aptJarDependencies()

        return allDeps
    }

//    @Task(name = "runKapt", reverseDependsOn = arrayOf("compile"), runAfter = arrayOf("clean"))
    fun taskRunKapt(project: Project) : TaskResult {
        val flags = arrayListOf<String>()
        kaptConfigs[project.name]?.let { config ->

            fun kaptPluginFlag(flagValue: String): String {
                return "plugin:org.jetbrains.kotlin.kapt3:$flagValue"
            }

            val generated = generated(project, context, config.outputDir)
            val generatedSources = generatedSources(project, context, config.outputDir)
            File(generatedSources).mkdirs()

            val allDeps = allDependencies()
            flags.add("-Xplugin")
            flags.add(annotationProcessorDependency().jarFile.get().absolutePath)
            flags.add("-P")
            val kaptPluginFlags = arrayListOf<String>()
//                kaptPluginFlags.add(kaptPluginFlag("aptOnly=true"))

            kaptPluginFlags.add(kaptPluginFlag("sources=" + generatedSources))
            kaptPluginFlags.add(kaptPluginFlag("classes=" + generatedClasses(project, context, config.outputDir)))
            kaptPluginFlags.add(kaptPluginFlag("stubs=" + generatedStubs(project, context, config.outputDir)))
            kaptPluginFlags.add(kaptPluginFlag("verbose=true"))
            kaptPluginFlags.add(kaptPluginFlag("aptOnly=false"))
            val dependencies = dependencyManager.calculateDependencies(project, context,
                    Filters.EXCLUDE_OPTIONAL_FILTER,
                    listOf(Scope.COMPILE),
                    allDeps)
            dependencies.forEach {
                val jarFile = it.jarFile.get()
                kaptPluginFlags.add(kaptPluginFlag("apclasspath=$jarFile"))
            }

            flags.add(kaptPluginFlags.joinToString(","))
            listOf("-language-version", "1.1", " -api-version", "1.1").forEach {
                flags.add(it)
            }
            val sourceFiles =
//                    KFiles.findSourceFiles(project.directory,
//                            listOf("src/tmp/kotlin"),
//                            listOf("kt"))
//                        .toList()

                KFiles.findSourceFiles(project.directory, project.sourceDirectories, listOf("kt")).toList() +
                generatedSources
//
            val buildDirectory = File(KFiles.joinDir(project.directory, generated))
            val cai = CompilerActionInfo(project.directory, allDeps, sourceFiles, listOf(".kt"),
                    buildDirectory, flags, emptyList(), forceRecompile = true)

            println("FLAGS: " + flags.joinToString("\n"))
            println("  " + kaptPluginFlags.joinToString("\n  "))
            val cr = compilerUtils.invokeCompiler(project, context, kotlinPlugin.compiler, cai)
            println("")
        }

        return TaskResult()
    }

    // ICompilerFlagContributor
    override fun compilerFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>): List<String> {
        if (!suffixesBeingCompiled.contains("java")) return emptyList()

        val result = arrayListOf<String>()

        fun addFlags(outputDir: String) {
            aptDependencies[project.name]?.let {
                result.add("-s")
                result.add(generatedSources(project, context, outputDir))
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
