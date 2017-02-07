package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.Variant
import com.beust.kobalt.internal.ILogger
import com.beust.kobalt.internal.IncrementalManager
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.MavenId
import com.beust.kobalt.maven.PomGenerator
import com.beust.kobalt.maven.SimpleDep
import com.beust.kobalt.maven.aether.KobaltMavenResolver
import com.beust.kobalt.misc.KobaltExecutors
import java.io.File

class KobaltContext(val args: Args) {
    lateinit var variant: Variant
    val profiles = arrayListOf<String>()

    init {
        args.profiles?.split(',')?.filterNotNull()?.forEach {
            profiles.add(it)
        }
    }

    fun findPlugin(name: String) = Plugins.findPlugin(name)

    /**
     * Files that can be resolved in the local cache.
     */
    enum class FileType { JAR, POM, SOURCES, JAVADOC, OTHER }

    /**
     * @param{id} is the Maven coordinate (e.g. "org.testng:testng:6.9.11"). If you are looking for a file
     * that is not described by the enum (e.g. "aar"), use OTHER and make sure your @param{id} contains
     * the fully qualified id (e.g. "com.example:example::aar:1.0").
     */
    fun fileFor(id: String, fileType: FileType) : File {
        val dep = SimpleDep(MavenId.create(id))
        fun toQualifier(dep: SimpleDep, ext: String, qualifier: String?) =
                dep.groupId + ":" + dep.artifactId +
                    ":$ext" +
                    (if (qualifier != null) ":$qualifier" else "") +
                    ":" + dep.version
        val fullId =
            when (fileType) {
                FileType.JAR -> toQualifier(dep, "jar", null)
                FileType.POM -> toQualifier(dep, "pom", null)
                FileType.SOURCES -> toQualifier(dep, "", "sources")
                FileType.JAVADOC -> toQualifier(dep, "", "javadoc")
                FileType.OTHER -> id
            }
        val resolved = resolver.resolve(fullId).artifact
        if (resolved != null) {
            return resolved.file
        } else {
            throw KobaltException("Couldn't resolve $id")
        }
    }

    /**
     * @return the content of the pom.xml for the given project.
     */
    fun generatePom(project: Project) = pomGeneratorFactory.create(project).generate()

    /** All the projects that are being built during this run */
    val allProjects = arrayListOf<Project>()

    /** For internal use only */
    val internalContext = InternalContext()

    //
    // Injected
    //
    lateinit var pluginInfo: PluginInfo
    lateinit var pluginProperties: PluginProperties
    lateinit var dependencyManager: DependencyManager
    lateinit var executors: KobaltExecutors
    lateinit var settings: KobaltSettings
    lateinit var incrementalManager: IncrementalManager
    lateinit var resolver: KobaltMavenResolver
    lateinit var pomGeneratorFactory: PomGenerator.IFactory
    lateinit var logger: ILogger
}

class InternalContext {
    /**
     * When an incremental task decides it's up to date, it sets this boolean to true so that subsequent
     * tasks in that project can be skipped as well. This is an internal field that should only be set by Kobalt.
     */
    private val incrementalSuccesses = hashSetOf<String>()
    fun previousTaskWasIncrementalSuccess(projectName: String) = incrementalSuccesses.contains(projectName) ?: false
    fun setIncrementalSuccess(projectName: String) = incrementalSuccesses.add(projectName)

    /**
     * Keep track of whether the build file was modified. If this boolean is true, incremental compilation
     * will be disabled.
     */
    var buildFileOutOfDate: Boolean = false

    /**
     * The absolute directory of the current project.
     */
    var absoluteDir: File? = null
}