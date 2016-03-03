package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.*
import com.beust.kobalt.IFileSpec.FileSpec
import com.beust.kobalt.IFileSpec.GlobSpec
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.PomGenerator
import com.beust.kobalt.misc.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Paths
import java.util.*
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackagingPlugin @Inject constructor(val dependencyManager : DependencyManager,
        val executors: KobaltExecutors, val jarGenerator: JarGenerator, val warGenerator: WarGenerator,
        val zipGenerator: ZipGenerator, val taskContributor: TaskContributor,
        val pomFactory: PomGenerator.IFactory, val configActor: ConfigActor<InstallConfig>)
            : BasePlugin(), IConfigActor<InstallConfig> by configActor, ITaskContributor, IAssemblyContributor {

    companion object {
        const val PLUGIN_NAME = "Packaging"

        @ExportedProjectProperty(doc = "Where the libraries are saved", type = "String")
        const val LIBS_DIR = "libsDir"

        @ExportedProjectProperty(doc = "The name of the jar file", type = "String")
        const val JAR_NAME = "jarName"

        @ExportedProjectProperty(doc = "The list of packages produced for this project",
                type = "List<PackageConfig>")
        const val PACKAGES = "packages"

        const val TASK_ASSEMBLE: String = "assemble"
        const val TASK_INSTALL: String = "install"

        fun findIncludedFiles(directory: String, files: List<IncludedFile>, excludes: List<Glob>)
                : List<IncludedFile> {
            val result = arrayListOf<IncludedFile>()
            files.forEach { includedFile ->
                val includedSpecs = arrayListOf<IFileSpec>()
                includedFile.specs.forEach { spec ->
                    val fromPath = includedFile.from
                    if (File(directory, fromPath).exists()) {
                        spec.toFiles(directory, fromPath).forEach { file ->
                            val fullFile = File(KFiles.joinDir(directory, fromPath, file.path))
                            if (! fullFile.exists()) {
                                throw AssertionError("File should exist: $fullFile")
                            }

                            if (!KFiles.isExcluded(fullFile, excludes)) {
                                val normalized = Paths.get(file.path).normalize().toFile().path
                                includedSpecs.add(FileSpec(normalized))
                            } else {
                                log(2, "Not adding ${file.path} to jar file because it's excluded")
                            }

                        }
                    } else {
                        log(2, "Directory $fromPath doesn't exist, not including it in the jar")
                    }
                }
                if (includedSpecs.size > 0) {
                    log(3, "Including specs $includedSpecs")
                    result.add(IncludedFile(From(includedFile.from), To(includedFile.to), includedSpecs))
                }
            }
            return result
        }

        fun generateArchive(project: Project,
                context: KobaltContext,
                archiveName: String?,
                suffix: String,
                includedFiles: List<IncludedFile>,
                expandJarFiles : Boolean = false,
                outputStreamFactory: (OutputStream) -> ZipOutputStream = DEFAULT_STREAM_FACTORY) : File {
            val fullArchiveName = context.variant.archiveName(project, archiveName, suffix)
            val archiveDir = File(libsDir(project))
            val result = File(archiveDir.path, fullArchiveName)
            log(2, "Creating $result")
            if (! Features.USE_TIMESTAMPS || isOutdated(project.directory, includedFiles, result)) {
                val outStream = outputStreamFactory(FileOutputStream(result))
                JarUtils.addFiles(project.directory, includedFiles, outStream, expandJarFiles)
                log(2, text = "Added ${includedFiles.size} files to $result")
                outStream.flush()
                outStream.close()
                log(1, "  Created $result")
            } else {
                log(2, "  $result is up to date")
            }

            project.projectProperties.put(JAR_NAME, result.absolutePath)

            return result
        }

        private val DEFAULT_STREAM_FACTORY = { os : OutputStream -> ZipOutputStream(os) }

        private fun isOutdated(directory: String, includedFiles: List<IncludedFile>, output: File): Boolean {
            if (! output.exists()) return true

            val lastModified = output.lastModified()
            includedFiles.forEach { root ->
                val allFiles = root.allFromFiles(directory)
                allFiles.forEach { relFile ->
                    val file = File(KFiles.joinDir(directory, root.from, relFile.path))
                    if (file.isFile) {
                        if (file.lastModified() > lastModified) {
                            log(2, "    TS - Outdated $file and $output "
                                    + Date(file.lastModified()) + " " + Date(output.lastModified()))
                            return true
                        }
                    }
                }
            }
            return false
        }

        private fun libsDir(project: Project) = KFiles.makeDir(KFiles.buildDir(project).path, "libs").path
    }

    override val name = PLUGIN_NAME

    private val packages = arrayListOf<PackageConfig>()

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        project.projectProperties.put(LIBS_DIR, libsDir(project))
        taskContributor.addVariantTasks(this, project, context, "assemble", runAfter = listOf("compile"),
                runTask = { doTaskAssemble(project) })
    }

    // IAssemblyContributor
    override fun assemble(project: Project, context: KobaltContext) : TaskResult {
        try {
            project.projectProperties.put(PACKAGES, packages)
            packages.filter { it.project.name == project.name }.forEach { pkg ->
                pkg.jars.forEach { jarGenerator.generateJar(pkg.project, context, it) }
                pkg.wars.forEach { warGenerator.generateWar(pkg.project, context, it) }
                pkg.zips.forEach { zipGenerator.generateZip(pkg.project, context, it) }
                if (pkg.generatePom) {
                    pomFactory.create(project).generate()
                }
            }
            return TaskResult()
        } catch(ex: Exception) {
            throw KobaltException(ex)
        }
    }

    @Task(name = TASK_ASSEMBLE, description = "Package the artifacts",
            runAfter = arrayOf(JvmCompilerPlugin.TASK_COMPILE))
    fun doTaskAssemble(project: Project) : TaskResult {
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


    @Task(name = PackagingPlugin.TASK_INSTALL, description = "Install the artifacts",
            runAfter = arrayOf(PackagingPlugin.TASK_ASSEMBLE))
    fun taskInstall(project: Project) : TaskResult {
        val config = configurationFor(project) ?: InstallConfig()
        val buildDir = project.projectProperties.getString(LIBS_DIR)
        val buildDirFile = File(buildDir)
        if (buildDirFile.exists()) {
            log(1, "Installing from $buildDir to ${config.libDir}")

            val toDir = KFiles.makeDir(config.libDir)
            KFiles.copyRecursively(buildDirFile, toDir, deleteFirst = true)
        }

        return TaskResult()
    }

    //ITaskContributor
    override fun tasksFor(context: KobaltContext): List<DynamicTask> = taskContributor.dynamicTasks
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
fun Project.assemble(init: PackageConfig.(p: Project) -> Unit) = let {
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
    fun jar(init: Jar.(p: Jar) -> Unit) : Jar {
        val jar = Jar()
        jar.init(jar)
        jars.add(jar)
        return jar
    }

    @Directive
    fun zip(init: Zip.(p: Zip) -> Unit) : Zip {
        val zip = Zip()
        zip.init(zip)
        zips.add(zip)
        return zip
    }

    @Directive
    fun war(init: War.(p: War) -> Unit) : War {
        val war = War()
        war.init(war)
        wars.add(war)
        return war
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
                    include(from(it), to(""), glob("**"))
                }
            }
        }
        jar {
            name = "${project.name}-${project.version}-javadoc.jar"
            include(from(JvmCompilerPlugin.DOCS_DIRECTORY), to(""), glob("**"))
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
        public fun manifest(init: Manifest.(p: Manifest) -> Unit) : Manifest {
            val m = Manifest(this)
            m.init(m)
            return m
        }
    }
}

open class Zip(open var name: String? = null) {
//    internal val includes = arrayListOf<IFileSpec>()
    internal val excludes = arrayListOf<Glob>()

    @Directive
    public fun from(s: String) = From(s)

    @Directive
    public fun to(s: String) = To(s)

    @Directive
    public fun exclude(vararg files: String) {
        files.forEach { excludes.add(Glob(it)) }
    }

    @Directive
    public fun exclude(vararg specs: Glob) {
        specs.forEach { excludes.add(it) }
    }

    @Directive
    public fun include(vararg files: String) {
        includedFiles.add(IncludedFile(files.map { FileSpec(it) }))
    }

    @Directive
    public fun include(from: From, to: To, vararg specs: String) {
        includedFiles.add(IncludedFile(from, to, specs.map { FileSpec(it) }))
    }

    @Directive
    public fun include(from: From, to: To, vararg specs: GlobSpec) {
        includedFiles.add(IncludedFile(from, to, listOf(*specs)))
    }

    /**
     * Prefix path to be removed from the zip file. For example, if you add "build/lib/a.jar" to the zip
     * file and the excludePrefix is "build/lib", then "a.jar" will be added at the root of the zip file.
     */
    val includedFiles = arrayListOf<IncludedFile>()

}

interface AttributeHolder {
    fun addAttribute(k: String, v: String)
}

/**
 * A jar is exactly like a zip with the addition of a manifest and an optional fatJar boolean.
 */
open class Jar(override var name: String? = null, var fatJar: Boolean = false) : Zip(name), AttributeHolder {
    @Directive
    public fun manifest(init: Manifest.(p: Manifest) -> Unit) : Manifest {
        val m = Manifest(this)
        m.init(m)
        return m
    }

    // Need to specify the version or attributes will just be dropped
    @Directive
    val attributes = arrayListOf(Pair("Manifest-Version", "1.0"))

    override fun addAttribute(k: String, v: String) {
        attributes.add(Pair(k, v))
    }
}

class War(override var name: String? = null) : Jar(name), AttributeHolder {
    init {
        include(from("src/main/webapp"),to(""), glob("**"))
        include(from("kobaltBuild/classes"), to("WEB-INF/classes"), glob("**"))
    }
}

class Pom {

}

class Manifest(val jar: AttributeHolder) {
    @Directive
    public fun attributes(k: String, v: String) {
        jar.addAttribute(k, v)
    }
}
