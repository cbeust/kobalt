package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.warn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KotlinPlugin @Inject constructor(
        override val localRepo: LocalRepo,
        override val files: KFiles,
        override val depFactory: DepFactory,
        override val dependencyManager: DependencyManager,
        override val executors: KobaltExecutors,
        override val jvmCompiler: JvmCompiler,
        override val taskContributor : TaskContributor)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors, jvmCompiler, taskContributor),
            IClasspathContributor, ICompilerContributor, IDocContributor {

    companion object {
        const val PLUGIN_NAME = "Kotlin"
    }

    override val name = PLUGIN_NAME

    override fun accept(project: Project) = project is KotlinProject

    override fun generateDoc(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        return TaskResult()
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
//                log(2, "Documentation generated in $outputDir")
//            } else {
//                log(2, "skip is true, not generating the documentation")
//            }
//        }
//        return TaskResult(success)
//    }

    override protected fun doTaskCompileTest(project: Project) : TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_TEST)
        val projectDir = File(project.directory)

        val sourceFiles = files.findRecursively(projectDir, project.sourceDirectoriesTest.map { File(it) })
        { it: String -> it.endsWith(project.sourceSuffix) }
                .map { File(projectDir, it).absolutePath }

        val result =
                if (sourceFiles.size > 0) {
                    compilePrivate(project, dependencyManager.testDependencies(project, context),
                            sourceFiles,
                            KFiles.makeOutputTestDir(project))
                } else {
                    warn("Couldn't find any source test files")
                    TaskResult()
                }

        lp(project, "Compilation of tests succeeded")
        return result
    }

    private fun compilePrivate(project: Project, cpList: List<IClasspathDependency>, sources: List<String>,
            outputDirectory: File): TaskResult {
        return kotlinCompilePrivate {
            classpath(cpList.map { it.jarFile.get().absolutePath })
            sourceFiles(sources)
            compilerArgs(compilerArgsFor(project))
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
            if (project == null || project is KotlinProject) {
                // All Kotlin projects automatically get the Kotlin runtime added to their class path
                listOf(getKotlinCompilerJar("kotlin-stdlib"), getKotlinCompilerJar("kotlin-runtime"))
                        .map { FileDependency(it) }
            } else {
                listOf()
            }

    // ICompilerContributor

    override fun affinity(project: Project, context: KobaltContext) =
            if (project.sourceSuffix == ".kt") 1 else 0

    override fun compile(project: Project, context: KobaltContext, info: CompilerActionInfo) : TaskResult {
        val result =
            if (info.sourceFiles.size > 0) {
                compilePrivate(project, info.dependencies, info.sourceFiles, info.outputDir)
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

    override fun toClassFile(sourceFile: String) = sourceFile + "Kt.class"
}

/**
 * @param project: the list of projects that need to be built before this one.
 */
@Directive
fun kotlinProject(vararg projects: Project, init: KotlinProject.() -> Unit): KotlinProject {
    return KotlinProject().apply {
        init()
        (Kobalt.findPlugin(KotlinPlugin.PLUGIN_NAME) as JvmCompilerPlugin).addDependentProjects(this, projects.toList())
    }
}

class KotlinCompilerConfig(val project: Project) {
    fun args(vararg options: String) {
        (Kobalt.findPlugin(KotlinPlugin.PLUGIN_NAME) as JvmCompilerPlugin).addCompilerArgs(project, *options)
    }
}

@Directive
fun Project.kotlinCompiler(init: KotlinCompilerConfig.() -> Unit) = let {
    KotlinCompilerConfig(it).init()
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
