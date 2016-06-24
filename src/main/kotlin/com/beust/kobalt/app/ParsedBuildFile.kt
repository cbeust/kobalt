package com.beust.kobalt.app

import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.internal.build.VersionFile
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.countChar
import com.beust.kobalt.misc.log
import com.beust.kobalt.plugin.kotlin.kotlinCompilePrivate
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.*

class ParsedBuildFile(val buildFile: BuildFile, val context: KobaltContext, val buildScriptUtil: BuildScriptUtil,
        val dependencyManager: DependencyManager, val files: KFiles) {
    val pluginList = arrayListOf<String>()
    val repos = arrayListOf<String>()
    val buildFileClasspath = arrayListOf<String>()
    val profileLines = arrayListOf<String>()
    val pluginUrls = arrayListOf<URL>()
    val projects = arrayListOf<Project>()

    private val preBuildScript = arrayListOf(
            "import com.beust.kobalt.*",
            "import com.beust.kobalt.api.*")
    val preBuildScriptCode : String get() = preBuildScript.joinToString("\n")

    private val buildScript = arrayListOf<String>()
    val buildScriptCode : String get() = buildScript.joinToString("\n")

    init {
        parseBuildFile()
        initPluginUrls()
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
             * If the current line matches one of the profile, turns the declaration into
             * val profile = true, otherwise return the same line
             */
            fun correctProfileLine(line: String) : String {
                (context.profiles as List<String>).forEach {
                    if (line.matches(Regex("[ \\t]*val[ \\t]+$it[ \\t]+=.*"))) {
                        with("val $it = true") {
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

    private fun initPluginUrls() {
        //
        // Compile and run preBuildScriptCode, which contains all the plugins() calls extracted. This
        // will add all the dynamic plugins found in this code to Plugins.dynamicPlugins
        //
        val pluginSourceFile = KFiles.createTempFile(".kt")
        pluginSourceFile.writeText(preBuildScriptCode, Charset.defaultCharset())
        log(2, "Saved ${pluginSourceFile.absolutePath}")

        //
        // Compile to preBuildScript.jar
        //
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
        projects.addAll(buildScriptUtil.runBuildScriptJarFile(buildScriptJarFile, arrayListOf<URL>(), context))

        //
        // All the plug-ins are now in Plugins.dynamicPlugins, download them if they're not already
        //
        Plugins.dynamicPlugins.forEach {
            pluginUrls.add(it.jarFile.get().toURI().toURL())
        }
    }

    private fun generateJarFile(context: KobaltContext, buildFile: BuildFile, buildScriptJarFile: File,
            originalFile: BuildFile) {
        val kotlintDeps = dependencyManager.calculateDependencies(null, context)
        val deps: List<String> = kotlintDeps.map { it.jarFile.get().absolutePath }
        val result = kotlinCompilePrivate {
            classpath(files.kobaltJar)
            classpath(deps)
            sourceFiles(buildFile.path.toFile().absolutePath)
            output = File(buildScriptJarFile.absolutePath)
        }.compile(context = context)
        if (! result.success) {
            throw KobaltException("Couldn't compile ${originalFile.realPath}:\n"
                    + result.errorMessage)
        }
    }
}

