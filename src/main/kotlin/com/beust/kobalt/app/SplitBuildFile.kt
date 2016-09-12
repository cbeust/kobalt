package com.beust.kobalt.app

import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.internal.build.VersionFile
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.countChar
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.plugin.kotlin.kotlinCompilePrivate
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.*

/**
 * Process the given build file (either with kotlinc or through scripting) and return projects and pluginUrls.
 */
class ProcessedBuildFile(val buildFile: BuildFile, val context: KobaltContext, val buildScriptUtil: BuildScriptUtil,
        val dependencyManager: DependencyManager, val files: KFiles) {
    val pluginUrls = arrayListOf<URL>()
    val splitFile = SplitBuildFile(buildFile, context, dependencyManager, files)

    fun compile(): BuildFileCompiler.FindProjectResult {

        // Find the projects but also invoke the plugins() directive, which will initialize Plugins.dynamicPlugins
        val projects = CompiledBuildFile(buildScriptUtil, dependencyManager, files)
                .findProjects(splitFile, context)

        // All the plug-ins are now in Plugins.dynamicPlugins, download them if they're not already
        Plugins.dynamicPlugins.forEach {
            pluginUrls.add(it.jarFile.get().toURI().toURL())
        }

        return BuildFileCompiler.FindProjectResult(context, projects, pluginUrls, TaskResult())
    }
}

/**
 * Compile a build file with kotlinc.
 */
class CompiledBuildFile(val buildScriptUtil: BuildScriptUtil, val dependencyManager: DependencyManager,
        val files: KFiles) {

    fun findProjects(splitFile: SplitBuildFile, context: KobaltContext): List<Project> {
        //
        // Compile and run preBuildScriptCode, which contains all the plugins() calls extracted. This
        // will add all the dynamic plugins found in this code to Plugins.dynamicPlugins
        //
        val pluginSourceFile = KFiles.createTempFile(".kt", deleteOnExit = true)
        pluginSourceFile.writeText(splitFile.preBuildScriptCode, Charset.defaultCharset())
        kobaltLog(2, "Saved ${pluginSourceFile.absolutePath}")

        //
        // Compile to preBuildScript.jar
        //
        val buildFile = splitFile.buildFile
        val buildScriptJar = KFiles.findBuildScriptLocation(buildFile, "preBuildScript.jar")
        val buildScriptJarFile = File(buildScriptJar)
        if (! buildScriptUtil.isUpToDate(buildFile, File(buildScriptJar))) {
            buildScriptJarFile.parentFile.mkdirs()
            generateJarFile(context, BuildFile(Paths.get(pluginSourceFile.path), "Plugins",
                    Paths.get(buildScriptJar)), buildScriptJarFile, buildFile)
            VersionFile.generateVersionFile(buildScriptJarFile.parentFile)
            Kobalt.context!!.internalContext.buildFileOutOfDate = true
        }

        //
        // Run preBuildScript.jar to initialize plugins and repos
        //
        val result = arrayListOf<Project>()
        result.addAll(buildScriptUtil.runBuildScriptJarFile(buildScriptJarFile, arrayListOf<URL>(), context))

        return result
    }

    private fun generateJarFile(context: KobaltContext, buildFile: BuildFile, buildScriptJarFile: File,
            originalFile: BuildFile) {

        //
        // Compile the jar file
        //
        val kotlintDeps = dependencyManager.calculateDependencies(null, context)
        val deps: List<String> = kotlintDeps.map { it.jarFile.get().absolutePath }
        val outputJar = File(buildScriptJarFile.absolutePath)
        val result = kotlinCompilePrivate {
            classpath(files.kobaltJar)
            classpath(deps)
            sourceFiles(buildFile.path.toFile().absolutePath)
            output = outputJar
        }.compile(context = context)
        if (! result.success) {
            throw KobaltException("Couldn't compile ${originalFile.realPath}:\n"
                    + result.errorMessage)
        }
    }
}

/**
 * Parse the given build file and split it into
 * - A simple build file with just the repos() and plugins() lines (preBuildScriptCode)
 * - The full build files with profiles applied (buildScriptCode)
 */
class SplitBuildFile(val buildFile: BuildFile, val context: KobaltContext, val dependencyManager: DependencyManager,
        val files: KFiles) {
    private val pluginList = arrayListOf<String>()
    private val repos = arrayListOf<String>()
    private val buildFileClasspath = arrayListOf<String>()
    private val profileLines = arrayListOf<String>()
    private val activeProfiles = arrayListOf<String>()

    private val preBuildScript = arrayListOf(
            "import com.beust.kobalt.*",
            "import com.beust.kobalt.api.*")
    val preBuildScriptCode: String get() = preBuildScript.joinToString("\n")

    private val buildScript = arrayListOf<String>()
    val buildScriptCode: String get() = buildScript.joinToString("\n")

    init {
        parseBuildFile()
    }

    private fun parseBuildFile() {
        var parenCount = 0
        var current: ArrayList<String>? = null
        buildFile.path.toFile().forEachLine(Charset.defaultCharset()) { line ->
            var index = line.indexOf("plugins(")
            if (current == null) {
                if (index >= 0) {
                    current = pluginList
                } else {
                    index = line.indexOf("repos(")
                    if (index >= 0) {
                        current = repos
                    } else {
                        index = line.indexOf("buildFileClasspath(")
                        if (index >= 0) {
                            current = buildFileClasspath
                        }
                    }
                }
            }

            if (parenCount > 0 || current != null) {
                if (index == -1) index = 0
                with(line.substring(index)) {
                    parenCount += line countChar '('
                    if (parenCount > 0) {
                        current!!.add(line)
                    }
                    parenCount -= line countChar ')'
                }
            }

            if (parenCount == 0) {
                current = null
            }

            /**
             * If the current line matches one of the profiles, turn the declaration into
             * val profile = true, otherwise return the same line
             */
            fun correctProfileLine(line: String): String {
                (context.profiles as List<String>).forEach {
                    if (line.matches(Regex("[ \\t]*val[ \\t]+$it[ \\t]*=.*"))) {
                        with("val $it = true") {
                            kobaltLog(2, "Activating profile $it in build file")
                            activeProfiles.add(it)
                            profileLines.add(this)
                            return this
                        }
                    }
                }
                return line
            }

            buildScript.add(correctProfileLine(line))
        }

        repos.forEach { preBuildScript.add(it) }
        pluginList.forEach { preBuildScript.add(it) }
        buildFileClasspath.forEach { preBuildScript.add(it) }
    }
}


