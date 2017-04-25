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
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.benchmarkMillis
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackagingPlugin @Inject constructor(val dependencyManager : DependencyManager,
        val incrementalManagerFactory: IncrementalManager.IFactory,
        val executors: KobaltExecutors, val jarGenerator: JarGenerator, val warGenerator: WarGenerator,
        val zipGenerator: ZipGenerator, val taskContributor: TaskContributor,
        val kobaltLog: ParallelLogger,
        val pomFactory: PomGenerator.IFactory, val configActor: ConfigsActor<InstallConfig>)
            : BasePlugin(), ITaskContributor, IIncrementalAssemblyContributor,
        IConfigsActor<InstallConfig> by configActor {

    companion object {
        const val PLUGIN_NAME = "Packaging"

        @ExportedProjectProperty(doc = "Where the libraries are saved", type = "String")
        const val LIBS_DIR = "libsDir"

        @ExportedProjectProperty(doc = "The list of packages produced for this project",
                type = "List<PackageConfig>")
        const val PACKAGES = "packages"

        const val TASK_ASSEMBLE: String = "assemble"
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

        configurationFor(project)?.let { configs ->
            configs.forEach { config ->
                taskContributor.addTask(this, project, config.taskName,
                        description = "Install to \"" + config.target + "\"",
                        group = "build",
                        dependsOn = listOf(PackagingPlugin.TASK_ASSEMBLE),
                        runTask = { taskInstall(project, context, config) })
                taskContributor.addVariantTasks(this, project, context, "config.taskName",
                        dependsOn = listOf("assemble"),
                        runTask = { taskInstall(project, context, config) })
            }
        }

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
                val jarsWithMainClass = arrayListOf<String>()

                allConfigs.forEach { packageConfig ->
                    packageConfig.jars.forEach {
                        if (it.attributes.any{ it.first == "Main-Class"}) {
                            jarsWithMainClass.add(it.name)
                        }
                    }

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

                if (jarsWithMainClass.any()) {
                    project.projectProperties.put(Archives.JAR_NAME_WITH_MAIN_CLASS, jarsWithMainClass[0])
                }
                project.projectProperties.put(Archives.JAR_NAME,
                        context.variant.archiveName(project, null, ".jar"))

                // Turn the IncludedFiles into actual Files
                val inputFiles = KFiles.materializeIncludedFiles(project, allIncludedFiles)

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


    private fun taskInstall(project: Project, context: KobaltContext, config: InstallConfig) : TaskResult {
        val buildDir = project.projectProperties.getString(LIBS_DIR)
        val buildDirFile = File(buildDir)
        if (buildDirFile.exists()) {

            if (config.includedFiles.isEmpty()) {
                context.logger.log(project.name, 1, "  Installing from $buildDir to ${config.target}")
                val toDir = KFiles.makeDir(config.target)
                File(buildDir).copyRecursively(toDir, overwrite = true)
            } else {
                // Delete all target directories
                config.includedFiles.map { File(it.to) }.distinct().forEach { targetFile ->
                    val isFile = targetFile.isFile
                    context.logger.log(project.name, 2, "  Deleting target dir $targetFile")
                    targetFile.deleteRecursively()
                    if (! isFile) targetFile.mkdirs()
                }
                // Perform the installations
                config.includedFiles.forEach { inf ->
                    val targetFile = File(inf.to)
                    val files = KFiles.materializeIncludedFiles(project, listOf(inf))
                    files.forEach {
                        context.logger.log(project.name, 1, "  Installing $it to $targetFile")
                        KFiles.copyRecursively(it, targetFile, true)
                    }
                }
            }
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

class InstallConfig(var target : String = "libs", var taskName : String = "install") : IncludeFromTo()

@Directive
fun Project.assemble(init: PackageConfig.(p: Project) -> Unit): PackageConfig = let {
    PackageConfig(this).apply { init(it) }
}

class PackageConfig(val project: Project) : AttributeHolder {
    val jars = arrayListOf<Jar>()
    val wars = arrayListOf<War>()
    val zips = arrayListOf<Zip>()
    var generatePom: Boolean = false

    init {
        (Kobalt.findPlugin(PackagingPlugin.PLUGIN_NAME) as PackagingPlugin).addPackage(this)
    }

    @Directive
    fun jar(init: Jar.(p: Jar) -> Unit) = Jar(project).apply {
        init(this)
        jars.add(this)
    }

    @Directive
    fun zip(init: Zip.(p: Zip) -> Unit) = Zip(project).apply {
        init(this)
        zips.add(this)
    }

    @Directive
    fun war(init: War.(p: War) -> Unit) = War(project).apply {
        init(this)
        wars.add(this)
    }

    /**
     * Package all the jar files necessary for a maven repo: classes, sources, javadocs.
     */
    @Directive
    fun mavenJars(init: MavenJars.(p: MavenJars) -> Unit) : MavenJars {
        val m = MavenJars(this)
        m.init(m)

        val mainJar = jar {
            fatJar = m.fatJar
        }
        jar {
            name = "${project.name}-${project.version}-sources.jar"
            project.sourceDirectories.forEach {
                if (File(project.directory, it).exists()) {
                    include(From(it), To(""), glob("**"))
                }
            }
        }
        jar {
            name = "${project.name}-${project.version}-javadoc.jar"
            val fromDir = KFiles.joinDir(project.buildDirectory, JvmCompilerPlugin.DOCS_DIRECTORY)
            include(From(fromDir), To(""), glob("**"))
        }

        mainJarAttributes.forEach {
            mainJar.addAttribute(it.first, it.second)
        }

        generatePom = true

        return m
    }

    val mainJarAttributes = arrayListOf<Pair<String, String>>()

    override fun addAttribute(k: String, v: String) {
        mainJarAttributes.add(Pair(k, v))
    }

    class MavenJars(val ah: AttributeHolder, var fatJar: Boolean = false, var manifest: Manifest? = null) :
            AttributeHolder by ah {
        fun manifest(init: Manifest.(p: Manifest) -> Unit) : Manifest {
            val m = Manifest(this)
            m.init(m)
            return m
        }
    }
}

class Pom {

}

fun main(args: Array<String>) {
    val realSource = File("/tmp/a")
    val sourceDir = File(realSource, "b").apply { mkdirs() }
    val from = File(sourceDir, "foo").apply { writeText("I'm a file") }
    val to = File("/tmp/to").apply {
        deleteRecursively()
        mkdirs()
    }
    val sourcePath = Paths.get(realSource.toURI())
    val targetPath = Paths.get(to.toURI())
//    Files.walkFileTree(sourcePath, KFiles.Companion.CopyFileVisitor(targetPath))

    if (! to.isDirectory) {
        throw AssertionError("Should be a directory")
    }
}
