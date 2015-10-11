package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.IFileSpec.FileSpec
import com.beust.kobalt.IFileSpec.Glob
import com.beust.kobalt.IFileSpec
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.glob
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.MavenDependency
import com.beust.kobalt.maven.SimpleDep
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.ToString
import com.beust.kobalt.plugin.java.JavaPlugin
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import java.util.jar.JarOutputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Directive
public fun assemble(project: Project, init: Package.(p: Project) -> Unit): Package {
    val pd = Package(project)
    pd.init(project)
    return pd
}

@Singleton
public class PackagingPlugin @Inject constructor(val dependencyManager : DependencyManager,
        val executors: KobaltExecutors, val localRepo: LocalRepo) : BasePlugin(), KobaltLogger {

    companion object {
        public const val TASK_ASSEMBLE : String = "assemble"
    }

    override val name = "packaging"

    private val packages = arrayListOf<Package>()

    @Task(name = TASK_ASSEMBLE, description = "Package the artifacts", runAfter = arrayOf(JavaPlugin.TASK_COMPILE))
    fun taskAssemble(project: Project) : TaskResult {
        packages.filter { it.project.name == project.name }.forEach { pkg ->
            pkg.jars.forEach { generateJar(pkg.project, it) }
            pkg.wars.forEach { generateWar(pkg.project, it) }
            pkg.zips.forEach { generateZip(pkg.project, it) }
        }
        return TaskResult()
    }

    private fun isExcluded(file: File, excludes: List<Glob>) : Boolean {
        if (excludes.isEmpty()) {
            return false
        } else {
            val ex = arrayListOf<PathMatcher>()
            excludes.forEach {
                ex.add(FileSystems.getDefault().getPathMatcher("glob:${it.spec}"))
            }
            ex.forEach {
                if (it.matches(Paths.get(file.getName()))) {
                    log(2, "Excluding ${file}")
                    return true
                }
            }
        }
        return false
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
        // Transitive closure of libraries into WEB-INF/libs
        // Copy them all in kobaltBuild/war/WEB-INF/libs and created one IncludedFile out of that directory
        //
        val allDependencies = dependencyManager.transitiveClosure(project.compileDependencies)

        val WEB_INF = "WEB-INF/lib"
        val outDir = project.buildDirectory + "/war"
        val fullDir = outDir + "/" + WEB_INF
        File(fullDir).mkdirs()
        allDependencies.map { it.jarFile.get() }.forEach {
            KFiles.copy(Paths.get(it.absolutePath), Paths.get(fullDir, it.name),
                    StandardCopyOption.REPLACE_EXISTING)
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
        val buildDir = KFiles.makeDir(project.directory, project.buildDirectory!!)
        val allFiles = arrayListOf<IncludedFile>()
        val classesDir = KFiles.makeDir(buildDir.path, "classes")

        if (jar.includedFiles.isEmpty()) {
            // If no includes were specified, assume the user wants a simple jar file made of the
            // classes of the project, so we specify a From("build/classes/"), To("") and
            // a list of files containing everything under it
            val relClassesDir = Paths.get(project.directory).relativize(Paths.get(classesDir.absolutePath + "/"))
            val prefixPath = Paths.get(project.directory).relativize(Paths.get(classesDir.path + "/"))

            // Class files
            val files = KFiles.findRecursively(classesDir).map { File(relClassesDir.toFile(), it) }
            val filesNotExcluded : List<File> = files.filter { ! isExcluded(it, jar.excludes) }
            val fileSpecs = arrayListOf<IFileSpec>()
            filesNotExcluded.forEach {
                fileSpecs.add(FileSpec(it.path.toString().substring(prefixPath.toString().length() + 1)))
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

            listOf(dependencyManager.transitiveClosure(project.compileDependencies),
                    dependencyManager.transitiveClosure(project.compileRuntimeDependencies)).forEach { dep ->
                dep.map { it.jarFile.get() }.forEach {
                    if (!isExcluded(it, jar.excludes)) {
                        allFiles.add(IncludedFile(arrayListOf(FileSpec(it.path))))
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

    private fun findIncludedFiles(directory: String, files: List<IncludedFile>, excludes: List<IFileSpec.Glob>)
            : List<IncludedFile> {
        val result = arrayListOf<IncludedFile>()
        files.forEach { includedFile ->
            val includedSpecs = arrayListOf<IFileSpec>()
            includedFile.specs.forEach { spec ->
                val fromPath = directory + "/" + includedFile.from
                if (File(fromPath).exists()) {
                    spec.toFiles(fromPath).forEach { file ->
                        if (!File(fromPath, file.path).exists()) {
                            throw AssertionError("File should exist: ${file}")
                        }

                        if (!isExcluded(file, excludes)) {
                            includedSpecs.add(FileSpec(file.path))
                        } else {
                            log(2, "Not adding ${file.path} to jar file because it's excluded")
                        }

                    }
                } else {
                    warn("Directory ${fromPath} doesn't exist, not including it in the jar")
                }
            }
            if (includedSpecs.size() > 0) {
                log(3, "Including specs ${includedSpecs}")
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

    private fun generateArchive(project: Project, archiveName: String?, suffix: String,
            includedFiles: List<IncludedFile>,
            expandJarFiles : Boolean = false,
            outputStreamFactory: (OutputStream) -> ZipOutputStream = DEFAULT_STREAM_FACTORY) : File {
        val buildDir = KFiles.makeDir(project.directory, project.buildDirectory!!)
        val archiveDir = KFiles.makeDir(buildDir.path, "libs")
        val fullArchiveName = archiveName ?: arrayListOf(project.name!!, project.version!!).join("-") + suffix
        val result = File(archiveDir.path, fullArchiveName)
        val outStream = outputStreamFactory(FileOutputStream(result))
        log(2, "Creating ${result}")
        JarUtils.addFiles(project.directory, includedFiles, outStream, expandJarFiles)
        log(2, "Added ${includedFiles.size()} files to ${result}")
        outStream.flush()
        outStream.close()
        log(1, "Created ${result}")
        return result
    }

    fun addPackage(p: Package) {
        packages.add(p)
    }
}

class Package(val project: Project) : AttributeHolder {
    val jars = arrayListOf<Jar>()
    val wars = arrayListOf<War>()
    val zips = arrayListOf<Zip>()

    init {
        (Plugins.getPlugin("packaging") as PackagingPlugin).addPackage(this)
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
    public fun mavenJars(init: MavenJars.(p: MavenJars) -> Unit) : MavenJars {
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

private open class Direction(open val p: String) {
    override public fun toString() = path
    public val path: String get() = if (p.isEmpty() or p.endsWith("/")) p else p + "/"
}

class From(override val p: String) : Direction(p)

class To(override val p: String) : Direction(p)

class IncludedFile(val fromOriginal: From, val toOriginal: To, val specs: List<IFileSpec>) {
    constructor(specs: List<IFileSpec>) : this(From(""), To(""), specs)
    public val from: String get() = fromOriginal.path.replace("\\", "/")
    public val to: String get() = toOriginal.path.replace("\\", "/")
    override public fun toString() = ToString("IncludedFile",
            "files", specs.map { it.toString() }.join(", "),
            "from", from,
            "to", to)
        .s
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
