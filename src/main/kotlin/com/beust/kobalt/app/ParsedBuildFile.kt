package com.beust.kobalt.app

import com.beust.kobalt.KobaltException
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.internal.build.IBuildSources
import com.beust.kobalt.internal.build.VersionFile
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.*
import com.beust.kobalt.plugin.kotlin.kotlinCompilePrivate
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.util.regex.Pattern

class ParsedBuildFile(val buildSources: IBuildSources, val context: KobaltContext, val buildScriptUtil: BuildScriptUtil,
        val dependencyManager: DependencyManager, val files: KFiles) {
    private val profileLines = arrayListOf<String>()
    private val projects = arrayListOf<Project>()
    private val activeProfiles = arrayListOf<String>()
    private val preBuildScriptJar = KFiles.findBuildScriptLocation(buildSources, "preBuildScript.jar")
    private val preBuildScriptJarFile = File(preBuildScriptJar)
    private val nonBuildScript = arrayListOf<String>()

    var containsProfiles = false
    val pluginUrls = arrayListOf<URL>()

    /**
     * Contains the addition of all the build files corrected with the active profiles and with
     * the buildScripts{} sections removed.
     */
    val nonBuildScriptCode : String get() = nonBuildScript.joinToString("\n")

    init {
        // Because profiles may have changed between two builds, we have to delete preBuildScript.jar file
        // every time and then generate a new one (or skip that phase if no buildScript{} was found in the
        // buid files)
        preBuildScriptJarFile.delete()

        val buildScriptInfo = parseBuildFile()

        // Only generate preBuildScript.jar if we found at least one buildScript{}
        if (buildScriptInfo != null) {
            parseBuildScriptInfo(buildScriptInfo)
        }
    }

    private fun parseBuildFile() : BuildScriptInfo? {
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

                if ((matcher != null && matcher.groups.isNotEmpty())
                        || (oldMatcher != null && oldMatcher.groups.isNotEmpty())) {
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

        //
        // Take all the build files and adjust them with the active profiles
        //
        val buildWithCorrectProfiles = arrayListOf<String>()
        buildSources.findSourceFiles().forEach {
            buildWithCorrectProfiles.addAll(applyProfiles(it.readLines()))
        }

        //
        // Now extract all the `buildScript{}` blocks from all these build files
        //
        val buildScriptInfo = BlockExtractor(Pattern.compile("^val.*buildScript.*\\{"), '{', '}')
                .extractBlock(buildWithCorrectProfiles)

        //
        // Write the build file to `nonBuildScript` excluding the buildScript{} directives since we already ran them
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

        return buildScriptInfo
    }

    /**
     * Generate preBuildScript.jar based on the buildScript{} found in the build files.
     */
    private fun parseBuildScriptInfo(buildScriptInfo: BuildScriptInfo) {
        //
        // Compile and run preBuildScriptCode, which contains all the plugins() calls extracted. This
        // will add all the dynamic plugins found in this code to Plugins.dynamicPlugins
        //
        val buildScriptSourceFile = KFiles.createTempBuildFileInTempDirectory(deleteOnExit = true)
        buildScriptSourceFile.writeText(buildScriptInfo.content, Charset.defaultCharset())
        kobaltLog(2, "Saved " + KFiles.fixSlashes(buildScriptSourceFile.absolutePath))

        //
        // Compile to preBuildScript.jar
        //

        // Because of profiles, it's not possible to find out if a preBuildScript.jar is up to date
        // or not so recompile it every time.
//        if (! buildScriptUtil.isUpToDate(buildFile, File(buildScriptJar))) {
            preBuildScriptJarFile.parentFile.mkdirs()
            generateJarFile(context, listOf(buildScriptSourceFile.path), preBuildScriptJarFile)
            VersionFile.generateVersionFile(preBuildScriptJarFile.parentFile)
            Kobalt.context!!.internalContext.buildFileOutOfDate = true
//        }

        //
        // Run preBuildScript.jar to initialize plugins and repos
        //
        projects.addAll(buildScriptUtil.runBuildScriptJarFile(preBuildScriptJarFile, arrayListOf<URL>(), context))

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

        context.internalContext.noIncrementalKotlin = saved
    }
}

