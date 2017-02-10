package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.TaskResult
import com.beust.kobalt.Variant
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.BaseJvmPlugin
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.KotlinJarFiles
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.warn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KotlinPlugin @Inject constructor(val executors: KobaltExecutors, val dependencyManager: DependencyManager,
        val settings: KobaltSettings, override val configActor: ConfigActor<KotlinConfig>,
        val kotlinJarFiles: KotlinJarFiles)
    : BaseJvmPlugin<KotlinConfig>(configActor), IDocContributor, IClasspathContributor, ICompilerContributor,
        IBuildConfigContributor {

    companion object {
        val PLUGIN_NAME = "Kotlin"
        val SOURCE_SUFFIXES = listOf("kt")
    }

    override val name = PLUGIN_NAME

    override fun sourceSuffixes() = SOURCE_SUFFIXES

    // IDocContributor
    override fun affinity(project: Project, context: KobaltContext) =
            if (project.sourceDirectories.any { it.contains("kotlin") }) 2 else 0

    override fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        return TaskResult()
    }

    // ICompilerFlagsContributor
    override fun compilerFlagsFor(project: Project, context: KobaltContext, currentFlags: List<String>,
            suffixesBeingCompiled: List<String>) : List<String> {
        val args = (configurationFor(project)?.args ?: listOf<String>()) + "-no-stdlib"
        val result = maybeCompilerArgs(compiler.sourceSuffixes, suffixesBeingCompiled, args)
        return result
    }

    //    override fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
//        val configs = dokkaConfigurations[project.name]
//        val classpath = context.dependencyManager.calculateDependencies(project, context)
//        val buildDir = project.buildDirectory
//        val classpathList = classpath.map { it.jarFile.get().absolutePath } + listOf(buildDir)
//        var success = true
//        configs.forEach { config ->
//            if (!config.skip) {
//                val outputDir = buildDir + "/" +
//                        if (config.outputDir.isBlank()) "doc" else config.outputDir
//
//                val gen = DokkaGenerator(
//                        KobaltDokkaLogger { success = false },
//                        classpathList,
//                        project.sourceDirectories.filter { File(it).exists() }.toList(),
//                        config.samplesDirs,
//                        config.includeDirs,
//                        config.moduleName,
//                        outputDir,
//                        config.outputFormat,
//                        config.sourceLinks.map { SourceLinkDefinition(it.dir, it.url, it.urlSuffix) }
//                )
//                gen.generate()
//                kobaltLog(2, "Documentation generated in $outputDir")
//            } else {
//                kobaltLog(2, "skip is true, not generating the documentation")
//            }
//        }
//        return TaskResult(success)
//    }

    class KotlinCompiler : ICompiler {
        override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo): TaskResult {
            return kotlinCompilePrivate {
                classpath(info.dependencies.map { it.jarFile.get().path })
                sourceFiles(info.sourceFiles)
                compilerArgs(info.compilerArgs)
                output = info.outputDir
            }.compile(project, context)
        }

    }

    private fun getKotlinCompilerJar(name: String): String {
        val id = "org.jetbrains.kotlin:$name:${settings.kobaltCompilerVersion}"
        val dep = dependencyManager.create(id)
        val result = dep.jarFile.get().absolutePath
        return result
    }

    // IClasspathContributor

    override fun classpathEntriesFor(project: Project?, context: KobaltContext): List<IClasspathDependency> =
        if (project == null || accept(project)) {
            // All Kotlin projects automatically get the Kotlin runtime added to their class path
            listOf(kotlinJarFiles.stdlib, kotlinJarFiles.runtime)
                    .map { FileDependency(it.absolutePath) }
        } else {
            emptyList()
        }

    // ICompilerContributor

    /** The Kotlin compiler should run before the Java one, hence priority - 5 */
    val compiler = CompilerDescription(PLUGIN_NAME, "kotlin", SOURCE_SUFFIXES, KotlinCompiler(),
            ICompilerDescription.DEFAULT_PRIORITY - 5, canCompileDirectories = true)

    override fun compilersFor(project: Project, context: KobaltContext)
            = if (sourceFileCount(project) > 0) listOf(compiler) else emptyList()

    //    private val dokkaConfigurations = ArrayListMultimap.create<String, DokkaConfig>()
//
//    fun addDokkaConfiguration(project: Project, dokkaConfig: DokkaConfig) {
//        dokkaConfigurations.put(project.name, dokkaConfig)
//    }

    override val buildConfigSuffix = "kt"

    override fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String,
            variant: Variant, buildConfigs: List<BuildConfig>): String {
        return KotlinBuildConfig().generateBuildConfig(project, context, packageName, variant, buildConfigs)
    }
}

/**
 * @param projects: the list of projects that need to be built before this one.
 */
@Directive
fun kotlinProject(vararg projects: Project, init: Project.() -> Unit): Project {
    return Project().apply {
        warn("kotlinProject{} is deprecated, please use project{}")
        init()
        (Kobalt.findPlugin(JvmCompilerPlugin.PLUGIN_NAME) as JvmCompilerPlugin)
                .addDependentProjects(this, projects.toList())
    }
}

class KotlinConfig(val project: Project) {
    val args = arrayListOf<String>()
    fun args(vararg options: String) = args.addAll(options)

    /** The version of the Kotlin compiler */
    var version: String? = null
}

@Directive
fun Project.kotlinCompiler(init: KotlinConfig.() -> Unit) = let {
    val config = KotlinConfig(it)
    config.init()
    (Kobalt.findPlugin(KotlinPlugin.PLUGIN_NAME) as KotlinPlugin).addConfiguration(this, config)
}

//class SourceLinkMapItem {
//    var dir: String = ""
//    var url: String = ""
//    var urlSuffix: String? = null
//}
//
//class DokkaConfig(
//        var samplesDirs: List<String> = emptyList(),
//        var includeDirs: List<String> = emptyList(),
//        var outputDir: String = "",
//        var outputFormat: String = "html",
//        var sourceLinks : ArrayList<SourceLinkMapItem> = arrayListOf<SourceLinkMapItem>(),
//        var moduleName: String = "",
//        var skip: Boolean = false) {
//
//    fun sourceLinks(init: SourceLinkMapItem.() -> Unit)
//            = sourceLinks.add(SourceLinkMapItem().apply { init() })
//}
