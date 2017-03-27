package com.beust.kobalt.app

import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.internal.build.BuildSources
import com.beust.kobalt.internal.build.IBuildSources
import com.beust.kobalt.internal.build.VersionFile
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.BlockExtractor
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.warn
import com.beust.kobalt.plugin.kotlin.kotlinCompilePrivate
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.util.regex.Pattern

class ParsedBuildFile(val buildSources: IBuildSources, val context: KobaltContext, val buildScriptUtil: BuildScriptUtil,
        val dependencyManager: DependencyManager, val files: KFiles) {
    val pluginList = arrayListOf<String>()
    val repos = arrayListOf<String>()
    val buildFileClasspath = arrayListOf<String>()
    val profileLines = arrayListOf<String>()
    val pluginUrls = arrayListOf<URL>()
    val projects = arrayListOf<Project>()
    val activeProfiles = arrayListOf<String>()

    var containsProfiles = false

    private val preBuildScript = arrayListOf(
            "import com.beust.kobalt.*",
            "import com.beust.kobalt.api.*")
    val preBuildScriptCode : String get() = preBuildScript.joinToString("\n")

    private val nonBuildScript = arrayListOf<String>()
    val buildScriptCode : String get() = nonBuildScript.joinToString("\n")

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
            (context.profiles as List<String>).forEach { profile ->
                val re = Regex(".*va[rl][ \\t]+([a-zA-Z0-9_]+)[ \\t]*.*profile\\(\\).*")
                val oldRe = Regex(".*va[rl][ \\t]+([a-zA-Z0-9_]+)[ \\t]*=[ \\t]*[tf][ra][ul][es].*")
                val matcher = re.matchEntire(line)
                val oldMatcher = oldRe.matchEntire(line)

                fun profileMatch(matcher: MatchResult?) : Pair<Boolean, String?> {
                    val variable = if (matcher != null) matcher.groups[1]?.value else null
                    return Pair(profile == variable, variable)
                }

                if ((matcher != null && matcher.groups.size > 0) || (oldMatcher != null && oldMatcher.groups.size> 0)) {
                    containsProfiles = true
                    val match = profileMatch(matcher)
                    val oldMatch = profileMatch(oldMatcher)
                    if (match.first || oldMatch.first) {
                        val variable = if (match.first) match.second else oldMatch.second

                        if (oldMatch.first) {
                            warn("Old profile syntax detected for \"$line\"," +
                                    " please update to \"val $variable by profile()\"")
                        }

                        with("val $variable = true") {
                            kobaltLog(2, "Activating profile $profile in build file")
                            activeProfiles.add(profile)
                            profileLines.add(this)
                            return this
                        }
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

        val buildWithCorrectProfiles = arrayListOf<String>()
        buildSources.findSourceFiles().forEach {
            buildWithCorrectProfiles.addAll(applyProfiles(it.readLines()))
        }

        val buildScriptInfo = BlockExtractor(Pattern.compile("^val.*buildScript.*\\{"), '{', '}')
                .extractBlock(buildWithCorrectProfiles)

        if (buildScriptInfo != null) {
            kobaltLog(2, "About to compile build file:\n=====\n" + buildScriptInfo.content + "\n=====")
            preBuildScript.add(buildScriptInfo.content)
        } else {
            repos.forEach { preBuildScript.add(it) }
            pluginList.forEach { preBuildScript.add(it) }
            buildFileClasspath.forEach { preBuildScript.add(it) }
        }

        //
        // Write the build file excluding the nonBuildScript{} tag since we already ran it
        //
        var lineNumber = 1
        buildSources.findSourceFiles().forEach { buildFile ->
            buildFile.forEachLine() { line ->
                if (buildScriptInfo == null || ! buildScriptInfo.isInSection(lineNumber)) {
                    val cpl = correctProfileLine(line)
                    if (cpl.startsWith("import")) nonBuildScript.add(0, cpl)
                    else nonBuildScript.add(cpl)
                }
                lineNumber++
            }
        }
    }

    private fun initPluginUrls() {
        //
        // Compile and run preBuildScriptCode, which contains all the plugins() calls extracted. This
        // will add all the dynamic plugins found in this code to Plugins.dynamicPlugins
        //
        val pluginSourceFile = KFiles.createTempBuildFileInTempDirectory(deleteOnExit = true)
        pluginSourceFile.writeText(preBuildScriptCode, Charset.defaultCharset())
        kobaltLog(2, "Saved " + KFiles.fixSlashes(pluginSourceFile.absolutePath))

        //
        // Compile to preBuildScript.jar
        //
        val buildScriptJar = KFiles.findBuildScriptLocation(buildSources, "preBuildScript.jar")
        val buildScriptJarFile = File(buildScriptJar)

        // Because of profiles, it's not possible to find out if a preBuildScript.jar is up to date
        // or not so recompile it every time.
//        if (! buildScriptUtil.isUpToDate(buildFile, File(buildScriptJar))) {
            buildScriptJarFile.parentFile.mkdirs()
            generateJarFile(context, listOf(pluginSourceFile.path), buildScriptJarFile)
            VersionFile.generateVersionFile(buildScriptJarFile.parentFile)
            Kobalt.context!!.internalContext.buildFileOutOfDate = true
//        }

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

    private fun generateJarFile(context: KobaltContext, sourceFiles: List<String>,
            buildScriptJarFile: File, originalFile: BuildFile? = null) {
        //
        // Compile the jar file
        //
        val kotlinDeps = dependencyManager.calculateDependencies(null, context)
        val deps: List<String> = kotlinDeps.map { it.jarFile.get().absolutePath }
        val outputJar = File(buildScriptJarFile.absolutePath)
        val saved = context.internalContext.noIncrementalKotlin
        val result = kotlinCompilePrivate {
            classpath(files.kobaltJar)
            classpath(deps)
            sourceFiles(sourceFiles)
            output = outputJar
            noIncrementalKotlin = true
        }.compile(context = context)
        if (! result.success) {
            val org = originalFile?.realPath ?: sourceFiles.joinToString(",")
            throw KobaltException("Couldn't compile $org:\n" + result.errorMessage)
        }
    }

    private fun generateJarFile(context: KobaltContext, buildSources: BuildSources,
            buildScriptJarFile: File)
        = generateJarFile(context, buildSources.findSourceFiles().map { it.path }, buildScriptJarFile)
}

