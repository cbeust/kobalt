package com.beust.kobalt.app

import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.internal.build.VersionFile
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.BlockExtractor
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.plugin.kotlin.kotlinCompilePrivate
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.regex.Pattern

class ParsedBuildFile(val buildFile: BuildFile, val context: KobaltContext, val buildScriptUtil: BuildScriptUtil,
        val dependencyManager: DependencyManager, val files: KFiles) {
    val pluginList = arrayListOf<String>()
    val repos = arrayListOf<String>()
    val buildFileClasspath = arrayListOf<String>()
    val profileLines = arrayListOf<String>()
    val pluginUrls = arrayListOf<URL>()
    val projects = arrayListOf<Project>()
    val activeProfiles = arrayListOf<String>()

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

        fun applyProfiles(lines: List<String>): List<String> {
            val result = arrayListOf<String>()
            lines.forEach { line ->
                result.add(correctProfileLine(line))
            }
            return result
        }

        val buildWithCorrectProfiles = applyProfiles(buildFile.path.toFile().readLines())
        val buildScriptInfo = BlockExtractor(Pattern.compile("^val.*buildScript.*\\{"), '{', '}')
                .extractBlock(buildWithCorrectProfiles)

        if (buildScriptInfo != null) {
            kobaltLog(3, "About to compile build file:\n=====\n" + buildScriptInfo.content + "\n=====")
            preBuildScript.add(buildScriptInfo.content)
        } else {
            repos.forEach { preBuildScript.add(it) }
            pluginList.forEach { preBuildScript.add(it) }
            buildFileClasspath.forEach { preBuildScript.add(it) }
        }

        //
        // Write the build file excluding the buildScript{} tag since we already ran it
        //
        var lineNumber = 1
        buildFile.path.toFile().forEachLine { line ->
            if (buildScriptInfo == null ||
                    (lineNumber < buildScriptInfo.startLine || lineNumber > buildScriptInfo.endLine)) {
                buildScript.add(correctProfileLine(line))
            }
            lineNumber++
        }
    }

    private fun initPluginUrls() {
        //
        // Compile and run preBuildScriptCode, which contains all the plugins() calls extracted. This
        // will add all the dynamic plugins found in this code to Plugins.dynamicPlugins
        //
        val pluginSourceFile = KFiles.createTempBuildFileInTempDirectory(deleteOnExit = true)
        pluginSourceFile.writeText(preBuildScriptCode, Charset.defaultCharset())
        kobaltLog(2, "Saved ${pluginSourceFile.absolutePath}")

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

        //
        // Compile the jar file
        //
        val kotlinDeps = dependencyManager.calculateDependencies(null, context)
        val deps: List<String> = kotlinDeps.map { it.jarFile.get().absolutePath }
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

