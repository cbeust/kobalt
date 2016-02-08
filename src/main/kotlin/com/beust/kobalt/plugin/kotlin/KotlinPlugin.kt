package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.TaskResult
import com.beust.kobalt.Variant
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.BaseJvmPlugin
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KotlinPlugin @Inject constructor(val executors: KobaltExecutors)
    : BaseJvmPlugin<KotlinConfig>(), IDocContributor, IClasspathContributor, ICompilerContributor,
        IBuildConfigContributor {

    companion object {
        const val PLUGIN_NAME = "Kotlin"
    }

    override val name = PLUGIN_NAME

    override fun accept(project: Project) = project.projectExtra.suffixesFound.contains("kt")

    // IDocContributor
    override fun affinity(project: Project, context: KobaltContext) =
            if (project.sourceDirectories.any { it.contains("kotlin") }) 2 else 0

    override fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        return TaskResult()
    }

    // ICompilerFlagsContributor
    override fun flagsFor(project: Project, context: KobaltContext, currentFlags: List<String>)
            = maybeCompilerArgs(project, (configurationFor(project)?.compilerArgs ?: listOf<String>()) +
                    listOf("-no-stdlib"))

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
//                log(2, "Documentation generated in $outputDir")
//            } else {
//                log(2, "skip is true, not generating the documentation")
//            }
//        }
//        return TaskResult(success)
//    }

    private fun compilePrivate(project: Project, cpList: List<IClasspathDependency>, sources: List<String>,
            outputDirectory: File, compilerArgs: List<String>): TaskResult {
        return kotlinCompilePrivate {
            classpath(cpList.map { it.jarFile.get().absolutePath })
            sourceFiles(sources)
            compilerArgs(compilerArgs)
            output = outputDirectory
        }.compile(project, context)
    }

    private fun getKotlinCompilerJar(name: String): String {
        val id = "org.jetbrains.kotlin:$name:${KotlinCompiler.KOTLIN_VERSION}"
        val dep = MavenDependency.create(id, executors.miscExecutor)
        val result = dep.jarFile.get().absolutePath
        return result
    }

    // interface IClasspathContributor
    override fun entriesFor(project: Project?): List<IClasspathDependency> =
            if (project == null || accept(project)) {
                // All Kotlin projects automatically get the Kotlin runtime added to their class path
                listOf(getKotlinCompilerJar("kotlin-stdlib"), getKotlinCompilerJar("kotlin-runtime"))
                        .map { FileDependency(it) }
            } else {
                listOf()
            }

    // ICompilerContributor

    override val sourceSuffixes = listOf("kt")

    override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val result =
            if (info.sourceFiles.size > 0) {
                compilePrivate(project, info.dependencies, info.sourceFiles, info.outputDir, info.compilerArgs)
            } else {
                warn("Couldn't find any source files")
                TaskResult()
            }

        lp(project, "Compilation " + if (result.success) "succeeded" else "failed")
        return result
    }

//    private val dokkaConfigurations = ArrayListMultimap.create<String, DokkaConfig>()
//
//    fun addDokkaConfiguration(project: Project, dokkaConfig: DokkaConfig) {
//        dokkaConfigurations.put(project.name, dokkaConfig)
//    }

    protected fun lp(project: Project, s: String) {
        log(2, "${project.name}: $s")
    }

    // IBuildConfigContributor
    override fun affinity(project: Project) = if (project.projectExtra.suffixesFound.contains("kotlin")) 2 else 0

    override val suffix = ".kt"

    override fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String,
            variant: Variant, buildConfigs: List<BuildConfig>): String {
        return KotlinBuildConfig().generateBuildConfig(project, context, packageName, variant, buildConfigs)
    }
}

/**
 * @param project: the list of projects that need to be built before this one.
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
    val compilerArgs = arrayListOf<String>()
    fun args(vararg options: String) = compilerArgs.addAll(options)
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
