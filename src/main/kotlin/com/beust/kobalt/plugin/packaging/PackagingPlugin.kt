package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.IFileSpec
import com.beust.kobalt.IFileSpec.FileSpec
import com.beust.kobalt.IFileSpec.Glob
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.ExportedProjectProperty
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.glob
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.toString
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Paths
import java.util.jar.JarOutputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackagingPlugin @Inject constructor(val dependencyManager : DependencyManager,
        val executors: KobaltExecutors, val localRepo: LocalRepo)
            : ConfigPlugin<InstallConfig>(), ITaskContributor {

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
    }

    override val name = PLUGIN_NAME

    private val packages = arrayListOf<PackageConfig>()

    val taskContributor : TaskContributor = TaskContributor()

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        project.projectProperties.put(LIBS_DIR, libsDir(project))
        taskContributor.addVariantTasks(this, project, context, "assemble", runAfter = listOf("compile"),
                runTask = { taskAssemble(project) })
    }

    private fun libsDir(project: Project) = KFiles.makeDir(buildDir(project).path, "libs").path

    @Task(name = TASK_ASSEMBLE, description = "Package the artifacts",
            runAfter = arrayOf(JvmCompilerPlugin.TASK_COMPILE))
    fun taskAssemble(project: Project) : TaskResult {
        project.projectProperties.put(PACKAGES, packages)
        packages.filter { it.project.name == project.name }.forEach { pkg ->
            pkg.jars.forEach { generateJar(pkg.project, it) }
            pkg.wars.forEach { generateWar(pkg.project, it) }
            pkg.zips.forEach { generateZip(pkg.project, it) }
        }
        return TaskResult()
    }

    private fun generateWar(project: Project, war: War) : File {
        //
        // src/main/web app and classes
        //
        val allFiles = arrayListOf(
                IncludedFile(From("src/main/webapp"), To(""), listOf(Glob("**"))),
                IncludedFile(From("kobaltBuild/classes"), To("WEB-INF/classes"), listOf(Glob("**")))
                )
        val manifest = java.util.jar.Manifest()//FileInputStream(mf))
        war.attributes.forEach { attribute ->
            manifest.mainAttributes.putValue(attribute.first, attribute.second)
        }

        //
        // The transitive closure of libraries goes into WEB-INF/libs.
        // Copy them all in kobaltBuild/war/WEB-INF/libs and create one IncludedFile out of that directory
        //
        val allDependencies = dependencyManager.calculateDependencies(project, context, projects,
                project.compileDependencies)

        val WEB_INF = "WEB-INF/lib"
        val outDir = project.buildDirectory + "/war"
        val fullDir = outDir + "/" + WEB_INF
        File(fullDir).mkdirs()

        // Run through all the classpath contributors and add their contributions to the libs/ directory
        context.pluginInfo.classpathContributors.map {
            it.entriesFor(project)
        }.map { deps : Collection<IClasspathDependency> ->
            deps.forEach { dep ->
                val jar = dep.jarFile.get()
                KFiles.copy(Paths.get(jar.path), Paths.get(fullDir, jar.name))
            }
        }

        // Add the regular dependencies to the libs/ directory
        allDependencies.map { it.jarFile.get() }.forEach {
            KFiles.copy(Paths.get(it.absolutePath), Paths.get(fullDir, it.name))
        }

        allFiles.add(IncludedFile(From(fullDir), To(WEB_INF), listOf(Glob("**"))))

        val jarFactory = { os:OutputStream -> JarOutputStream(os, manifest) }
        return generateArchive(project, war.name, ".war", allFiles,
                false /* don't expand jar files */, jarFactory)
    }

    private fun generateJar(project: Project, jar: Jar) : File {
        //
        // Add all the applicable files for the current project
        //
        val buildDir = buildDir(project)
        val allFiles = arrayListOf<IncludedFile>()
        val classesDir = KFiles.makeDir(buildDir.path, "classes")

        if (jar.includedFiles.isEmpty()) {
            // If no includes were specified, assume the user wants a simple jar file made of the
            // classes of the project, so we specify a From("build/classes/"), To("") and
            // a list of files containing everything under it
            val relClassesDir = Paths.get(project.directory).relativize(Paths.get(classesDir.path))
            val prefixPath = Paths.get(project.directory).relativize(Paths.get(classesDir.path + "/"))

            // Class files
            val files = KFiles.findRecursively(classesDir).map { File(relClassesDir.toFile(), it) }
            val filesNotExcluded : List<File> = files.filter { ! KFiles.isExcluded(it, jar.excludes) }
            val fileSpecs = arrayListOf<IFileSpec>()
            filesNotExcluded.forEach {
                fileSpecs.add(FileSpec(it.path.toString().substring(prefixPath.toString().length + 1)))
            }
            allFiles.add(IncludedFile(From(prefixPath.toString() + "/"), To(""), fileSpecs))
        } else {
            allFiles.addAll(findIncludedFiles(project.directory, jar.includedFiles, jar.excludes))
        }

        //
        // If fatJar is true, add all the transitive dependencies as well (both compile and runtime)
        //
        if (jar.fatJar) {
            log(2, "Creating fat jar")

            val seen = hashSetOf<String>()
            @Suppress("UNCHECKED_CAST")
            val dependentProjects = project.projectProperties.get(JvmCompilerPlugin.DEPENDENT_PROJECTS)
                    as List<ProjectDescription>
            listOf(dependencyManager.calculateDependencies(project, context, dependentProjects,
                        project.compileDependencies),
                    dependencyManager.calculateDependencies(project, context, dependentProjects,
                            project.compileRuntimeDependencies))
                        .forEach { deps : List<IClasspathDependency> ->
                                deps.map {
                                    it.jarFile.get()
                                }.forEach { file : File ->
                                    if (! seen.contains(file.name)) {
                                        seen.add(file.name)
                                        if (! KFiles.isExcluded(file, jar.excludes)) {
                                            allFiles.add(IncludedFile(arrayListOf(FileSpec(file.path))))
                                        }
                                    }
                                }
                        }
        }

        //
        // Generate the manifest
        //
        val manifest = java.util.jar.Manifest()//FileInputStream(mf))
        jar.attributes.forEach { attribute ->
            manifest.mainAttributes.putValue(attribute.first, attribute.second)
        }
        val jarFactory = { os:OutputStream -> JarOutputStream(os, manifest) }

        return generateArchive(project, jar.name, ".jar", allFiles,
                true /* expandJarFiles */, jarFactory)
    }

    private fun buildDir(project: Project) = KFiles.makeDir(project.directory, project.buildDirectory)

    private fun findIncludedFiles(directory: String, files: List<IncludedFile>, excludes: List<IFileSpec.Glob>)
            : List<IncludedFile> {
        val result = arrayListOf<IncludedFile>()
        files.forEach { includedFile ->
            val includedSpecs = arrayListOf<IFileSpec>()
            includedFile.specs.forEach { spec ->
                val fromPath = directory + "/" + includedFile.from
                if (File(fromPath).exists()) {
                    spec.toFiles(fromPath).forEach { file ->
                        File(fromPath, file.path).let {
                            if (! it.exists()) {
                                throw AssertionError("File should exist: $it")
                            }
                        }

                        if (! KFiles.isExcluded(file, excludes)) {
                            includedSpecs.add(FileSpec(file.path))
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

    private fun generateZip(project: Project, zip: Zip) {
        val allFiles = findIncludedFiles(project.directory, zip.includedFiles, zip.excludes)
        generateArchive(project, zip.name, ".zip", allFiles)
    }

    private val DEFAULT_STREAM_FACTORY = { os : OutputStream -> ZipOutputStream(os) }

    private fun generateArchive(project: Project,
            archiveName: String?,
            suffix: String,
            includedFiles: List<IncludedFile>,
            expandJarFiles : Boolean = false,
            outputStreamFactory: (OutputStream) -> ZipOutputStream = DEFAULT_STREAM_FACTORY) : File {
        val fullArchiveName = context.variant.archiveName(project, archiveName, suffix)
        val archiveDir = File(libsDir(project))
        val result = File(archiveDir.path, fullArchiveName)
        val outStream = outputStreamFactory(FileOutputStream(result))
        log(2, "Creating $result")
        JarUtils.addFiles(project.directory, includedFiles, outStream, expandJarFiles)
        log(2, text = "Added ${includedFiles.size} files to $result")
        outStream.flush()
        outStream.close()
        log(1, "  Created $result")

        project.projectProperties.put(JAR_NAME, result.absolutePath)

        return result
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
                include(from(it), to(""), glob("**${project.sourceSuffix}"))
            }
        }
        jar {
            name = "${project.name}-${project.version}-javadoc.jar"
            include(from(project.buildDirectory + "/" + JvmCompilerPlugin.DOCS_DIRECTORY), to(""), glob("**"))
        }

        mainJarAttributes.forEach {
            mainJar.addAttribute(it.first, it.second)
        }

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
    public fun include(from: From, to: To, vararg specs: Glob) {
        includedFiles.add(IncludedFile(from, to, listOf(*specs)))
    }

    /**
     * Prefix path to be removed from the zip file. For example, if you add "build/lib/a.jar" to the zip
     * file and the excludePrefix is "build/lib", then "a.jar" will be added at the root of the zip file.
     */
    val includedFiles = arrayListOf<IncludedFile>()

}

open class Direction(open val p: String) {
    override public fun toString() = path
    public val path: String get() = if (p.isEmpty() or p.endsWith("/")) p else p + "/"
}

class From(override val p: String) : Direction(p)

class To(override val p: String) : Direction(p)

class IncludedFile(val fromOriginal: From, val toOriginal: To, val specs: List<IFileSpec>) {
    constructor(specs: List<IFileSpec>) : this(From(""), To(""), specs)
    public val from: String get() = fromOriginal.path.replace("\\", "/")
    public val to: String get() = toOriginal.path.replace("\\", "/")
    override public fun toString() = toString("IncludedFile",
            "files", specs.map { it.toString() }.joinToString(", "),
            "from", from,
            "to", to)
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
