package com.beust.kobalt.plugin.packaging

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
import com.beust.kobalt.maven.Md5
import com.beust.kobalt.maven.PomGenerator
import com.beust.kobalt.misc.IncludedFile
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.benchmarkMillis
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

    override fun assemble(project: Project, context: KobaltContext) : IncrementalTaskInfo {
        val allConfigs = packages.filter { it.project.name == project.name }
        val zipToFiles = hashMapOf<String, List<IncludedFile>>()

        val benchmark = benchmarkMillis {
            if (true) {
                //
                // This loop prepares the data so we can calculate input and output checksums for the
                // assemble task:
                // - Input: Calculate the list of all the included files for every archive (jar/war/zip) and
                // store them in the `zipToFiles` map.
                // - Output: Calculate all the output archive files into `outputFiles`
                //
                // `zipToFiles` is used again after this loop so we can pass the list of included files we just
                // calculated for all the archives in the actual execution of the task, so we don't have to
                // look for them a second time.
                //
                val allIncludedFiles = arrayListOf<IncludedFile>()
                val outputFiles = arrayListOf<File>()
                allConfigs.forEach { packageConfig ->
                    listOf(packageConfig.jars, packageConfig.wars, packageConfig.zips).forEach { archives ->
                        archives.forEach {
                            val files = jarGenerator.findIncludedFiles(packageConfig.project, context, it)
                            val outputFile = jarGenerator.fullArchiveName(project, context, it.name)
                            outputFiles.add(outputFile)
                            allIncludedFiles.addAll(files)
                            zipToFiles[it.name] = files
                        }
                    }
                }

                // Turn the IncludedFiles into actual Files
                val inputFiles = allIncludedFiles.fold(arrayListOf<File>()) { files, includedFile: IncludedFile ->
                    val foundFiles = includedFile.allFromFiles(project.directory)
                    val absFiles = foundFiles.map {
                        File(KFiles.joinDir(project.directory, includedFile.from, it.path))
                    }
                    files.addAll(absFiles)
                    files
                }

                val inMd5 = Md5.toMd5Directories(inputFiles)
                val outMd5 = Md5.toMd5Directories(outputFiles)
                Pair(inMd5, outMd5)
            } else {
                Pair(null, null)
            }
        }

        context.logger.log(project.name, 2, "    Time to calculate packaging checksum: ${benchmark.first} ms")

        val (inMd5, outMd5) = benchmark.second

        return IncrementalTaskInfo(
                { -> inMd5 },
                { -> outMd5 },
                { project ->
                    try {
                        project.projectProperties.put(Archives.JAR_NAME,
                                context.variant.archiveName(project, null, ".jar"))

                        fun findFiles(ff: ArchiveGenerator, zip: Zip) : List<IncludedFile> {
                            val archiveName = ff.fullArchiveName(project, context, zip.name).name
                            return zipToFiles[archiveName]!!
                        }

                        allConfigs.forEach { packageConfig ->
                            val pairs = listOf(
                                    Pair(packageConfig.jars, jarGenerator),
                                    Pair(packageConfig.wars, warGenerator),
                                    Pair(packageConfig.zips, zipGenerator)
                                    )

                            pairs.forEach { pair ->
                                val zips = pair.first
                                val generator = pair.second
                                zips.forEach {
                                    generator.generateArchive(packageConfig.project, context, it,
                                            findFiles(generator, it))
                                }
                            }
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
                _: Project -> taskInfo }, context.variant)
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
//    fun generateManifest(project: Project): TaskResult {
//        val analyzer = Analyzer().apply {
//            jar = aQute.bnd.osgi.Jar(project.projectProperties.get(Archives.JAR_NAME) as String)
//            val dependencies = project.compileDependencies + project.compileRuntimeDependencies
//            dependencyManager.calculateDependencies(project, context, passedDependencies = dependencies).forEach {
//                addClasspath(it.jarFile.get())
//            }
//            setProperty(Analyzer.BUNDLE_VERSION, project.version)
//            setProperty(Analyzer.BUNDLE_NAME, project.group)
//            setProperty(Analyzer.BUNDLE_DESCRIPTION, project.description)
//            setProperty(Analyzer.IMPORT_PACKAGE, "*")
//            setProperty(Analyzer.EXPORT_PACKAGE, "*;-noimport:=false;version=" + project.version)
//        }
//
//        val manifest = analyzer.calcManifest()
//        manifest.write(System.out)
//        return TaskResult()
//    }


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

