package com.beust.kobalt.plugin.packaging

import aQute.bnd.osgi.Analyzer
import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.archive.*
import com.beust.kobalt.internal.IncrementalManager
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.ParallelLogger
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.PomGenerator
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackagingPlugin @Inject constructor(val dependencyManager : DependencyManager,
        val incrementalManagerFactory: IncrementalManager.IFactory,
        val executors: KobaltExecutors, val jarGenerator: JarGenerator, val warGenerator: WarGenerator,
        val zipGenerator: ZipGenerator, val taskContributor: TaskContributor,
        val kobaltLog: ParallelLogger,
        val pomFactory: PomGenerator.IFactory, val configActor: ConfigActor<InstallConfig>)
            : BasePlugin(), ITaskContributor, IIncrementalAssemblyContributor,
        IConfigActor<InstallConfig> by configActor {

    companion object {
        const val PLUGIN_NAME = "Packaging"

        @ExportedProjectProperty(doc = "Where the libraries are saved", type = "String")
        const val LIBS_DIR = "libsDir"

        @ExportedProjectProperty(doc = "The list of packages produced for this project",
                type = "List<PackageConfig>")
        const val PACKAGES = "packages"

        const val TASK_ASSEMBLE: String = "assemble"
        const val TASK_INSTALL: String = "install"
    }

    override val name = PLUGIN_NAME

    private val packages = arrayListOf<PackageConfig>()

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        project.projectProperties.put(LIBS_DIR, KFiles.libsDir(project))
        project.projectProperties.put(PACKAGES, packages)

        taskContributor.addVariantTasks(this, project, context, "assemble", group = "build",
                dependsOn = listOf("compile"),
                runTask = { doTaskAssemble(project) })
        taskContributor.addVariantTasks(this, project, context, "install",
                dependsOn = listOf("assemble"),
                runTask = { taskInstall(project) })
    }

    /**
     * "assemble" is an incremental task but with a twist. Because it can be costly to determine if any
     * of the class files generated in the previous phase is new or not, we just don't do that and always
     * return "null" for both input and output checksums, which would cause that task to always be rerun.
     * However, we are depending on Kobalt's cascading incremental management to skip us whenever appropriate:
     * whenever a previous incremental task was a success, all following incremental tasks are automatically
     * skipped.
     */
    override fun assemble(project: Project, context: KobaltContext) : IncrementalTaskInfo {
        return IncrementalTaskInfo(
                { null },
                { null },
                { project ->
                    try {
                        project.projectProperties.put(Archives.JAR_NAME,
                                context.variant.archiveName(project, null, ".jar"))
                        packages.filter { it.project.name == project.name }.forEach { packageConfig ->
                            packageConfig.jars.forEach { jarGenerator.generateJar(packageConfig.project, context, it) }
                            packageConfig.wars.forEach { warGenerator.generateWar(packageConfig.project, context, it) }
                            packageConfig.zips.forEach { zipGenerator.generateZip(packageConfig.project, context, it) }
                            if (packageConfig.generatePom) {
                                pomFactory.create(project).generateAndSave()
                            }
                        }
                        TaskResult()
            } catch(ex: Exception) {
                throw KobaltException(ex)
            }}, context)
    }

    @Task(name = TASK_ASSEMBLE, description = "Package the artifacts", group = JvmCompilerPlugin.GROUP_BUILD,
            dependsOn = arrayOf(JvmCompilerPlugin.TASK_COMPILE))
    fun doTaskAssemble(project: Project) : TaskResult {
        // Incremental assembly contributors
        context.pluginInfo.incrementalAssemblyContributors.forEach {
            val taskInfo = it.assemble(project, context)
            val closure = incrementalManagerFactory.create().toIncrementalTaskClosure(TASK_ASSEMBLE, {
                p: Project -> taskInfo }, context.variant)
            val thisResult = closure.invoke(project)
            if (! thisResult.success) {
                // Abort at the first failure
                return thisResult
            }
        }

        // Regular assembly contributors
        context.pluginInfo.assemblyContributors.forEach {
            val thisResult = it.assemble(project, context)
            if (! thisResult.success) {
                // Abort at the first failure
                return thisResult
            }
        }
        return TaskResult()
    }

    fun addPackage(p: PackageConfig) {
        packages.add(p)
    }

//    @Task(name = "generateOsgiManifest", alwaysRunAfter = arrayOf(TASK_ASSEMBLE))
    fun generateManifest(project: Project): TaskResult {
        val analyzer = Analyzer().apply {
            jar = aQute.bnd.osgi.Jar(project.projectProperties.get(Archives.JAR_NAME) as String)
            val dependencies = project.compileDependencies + project.compileRuntimeDependencies
            dependencyManager.calculateDependencies(project, context, passedDependencies = dependencies).forEach {
                addClasspath(it.jarFile.get())
            }
            setProperty(Analyzer.BUNDLE_VERSION, project.version)
            setProperty(Analyzer.BUNDLE_NAME, project.group)
            setProperty(Analyzer.BUNDLE_DESCRIPTION, project.description)
            setProperty(Analyzer.IMPORT_PACKAGE, "*")
            setProperty(Analyzer.EXPORT_PACKAGE, "*;-noimport:=false;version=" + project.version)
        }

        val manifest = analyzer.calcManifest()
        manifest.write(System.out)
        return TaskResult()
    }


    @Task(name = PackagingPlugin.TASK_INSTALL, description = "Install the artifacts",
            dependsOn = arrayOf(PackagingPlugin.TASK_ASSEMBLE))
    fun taskInstall(project: Project) : TaskResult {
        val config = configurationFor(project) ?: InstallConfig()
        val buildDir = project.projectProperties.getString(LIBS_DIR)
        val buildDirFile = File(buildDir)
        if (buildDirFile.exists()) {
            context.logger.log(project.name, 1, "Installing from $buildDir to ${config.libDir}")

            val toDir = KFiles.makeDir(config.libDir)
            KFiles.copyRecursively(buildDirFile, toDir, deleteFirst = true)
        }

        return TaskResult()
    }

    //ITaskContributor
    override fun tasksFor(project: Project, context: KobaltContext): List<DynamicTask> = taskContributor.dynamicTasks
}

@Directive
fun Project.install(init: InstallConfig.() -> Unit) {
    InstallConfig().let {
        it.init()
        (Kobalt.findPlugin(PackagingPlugin.PLUGIN_NAME) as PackagingPlugin).addConfiguration(this, it)
    }
}

class InstallConfig(var libDir : String = "libs")

@Directive
fun Project.assemble(init: PackageConfig.(p: Project) -> Unit): PackageConfig = let {
    PackageConfig(this).apply { init(it) }
}

class Pom {

}

