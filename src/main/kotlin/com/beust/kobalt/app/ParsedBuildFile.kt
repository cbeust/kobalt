package com.beust.kobalt.app

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.build.IBuildSources
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.BuildScriptInfo
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.warn
import com.google.inject.Singleton
import java.net.URL

@Singleton
class _ParsedBuildFile(val buildSources: IBuildSources, val context: KobaltContext, val buildScriptUtil:
BuildScriptUtil,
        val dependencyManager: DependencyManager, val files: KFiles) {
    private val profileLines = arrayListOf<String>()
    private val projects = arrayListOf<Project>()
    private val activeProfiles = arrayListOf<String>()
    private val nonBuildScript = arrayListOf<String>()

    var containsProfiles = false
    val pluginUrls = arrayListOf<URL>()

    /**
     * Contains the addition of all the build files corrected with the active profiles and with
     * the buildScript{} sections removed.
     */
    val nonBuildScriptCode : String get() = nonBuildScript.joinToString("\n")

    init {
        // Because profiles may have changed between two builds, we have to delete preBuildScript.jar file
        // every time and then generate a new one (or skip that phase if no buildScript{} was found in the
        // buid files)
//        preBuildScriptJarFile.delete()

//        val buildScriptInfos = parseBuildFile()

        // Only generate preBuildScript.jar if we found at least one buildScript{}
//        if (buildScriptInfos.isNotEmpty()) {
//            parseBuildScriptInfo(buildScriptInfos)
//        }

//        generateFinalBuildFile(buildScriptInfos)
    }

//    private fun generateFinalBuildFile(buildScriptInfos: List<BuildScriptInfo>) {
//        //
//        // Write the build file to `nonBuildScript` excluding the buildScript{} directives since we already ran them
//        //
//        var lineNumber = 1
//        buildSources.findSourceFiles().forEach { buildFile ->
//            val buildScriptInfo = buildScriptInfos.find { it.file == buildFile }
//            if (buildFile == buildScriptInfo?.file) {
//                println("Found file with buildScript in it: " + buildFile)
//            }
//            buildFile.forEachLine() { line ->
//                if (buildScriptInfo == null || ! buildScriptInfo.isInSection(lineNumber)) {
//                    val cpl = correctProfileLine(line)
//                    if (cpl.startsWith("import")) nonBuildScript.add(0, cpl)
//                    else nonBuildScript.add(cpl)
//                }
//                lineNumber++
//            }
//        }
//    }

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

//    private fun parseBuildFile() : List<BuildScriptInfo> {
//        fun applyProfiles(lines: List<String>): List<String> {
//            val result = arrayListOf<String>()
//            lines.forEach { line ->
//                result.add(correctProfileLine(line))
//
//            }
//            return result
//        }
//
//        //
//        // Take all the build files and adjust them with the active profiles
//        //
//        val buildScriptInfos = arrayListOf<BuildScriptInfo>()
//        val buildWithCorrectProfiles = arrayListOf<String>()
//        val buildFiles = buildSources.findSourceFiles()
//        buildFiles.forEach {
//            buildWithCorrectProfiles.addAll(applyProfiles(it.readLines()))
//
//            //
//            // Now extract all the `buildScript{}` blocks from all these build files
//            //
//            val lsi = BlockExtractor(Pattern.compile("^val.*buildScript.*\\{"), '{', '}')
//                    .extractBlock(it, buildWithCorrectProfiles)
//            if (lsi != null) buildScriptInfos.add(lsi)
//        }
//
//        return buildScriptInfos
//    }

    /**
     * Generate preBuildScript.jar based on the buildScript{} found in the build files.
     */
//    private fun parseBuildScriptInfo(buildScriptInfos: List<BuildScriptInfo>) {
//        buildScriptInfos.forEach { buildScriptInfo ->
//            buildScriptInfo.sections.forEach { section ->
//                val buildScriptSection = (buildScriptInfo.imports +
//                        buildScriptInfo.fullBuildFile.subList(section.start - 1, section.end))
//                        .joinToString("\n")
//                println("=== Compiling\n" + buildScriptSection + " for line " + (section.start - 1))
//
//                //
//                // Compile and run preBuildScriptCode, which contains all the plugins() calls extracted. This
//                // will add all the dynamic plugins found in this code to Plugins.dynamicPlugins
//                //
//                val buildScriptSourceFile = KFiles.createTempBuildFileInTempDirectory(deleteOnExit = true)
//                buildScriptSourceFile.writeText(buildScriptSection, Charset.defaultCharset())
//                kobaltLog(2, "Saved " + KFiles.fixSlashes(buildScriptSourceFile.absolutePath))
//
//                //
//                // Compile to preBuildScript.jar
//                //
//
//                val dir = preBuildScriptJarFile.parentFile
//                dir.mkdirs()
//                val bsJar = java.io.File(dir, "buildScript-" + section.start + ".jar")
//                generateJarFile(context, listOf(buildScriptSourceFile.path), bsJar)
//                VersionFile.generateVersionFile(preBuildScriptJarFile.parentFile)
//                Kobalt.context!!.internalContext.buildFileOutOfDate = true
//
//                //
//                // Run preBuildScript.jar to initialize plugins and repos
//                //
//                val currentDirs = arrayListOf<String>().apply { addAll(Kobalt.buildSourceDirs) }
//                projects.addAll(buildScriptUtil.runBuildScriptJarFile(bsJar, arrayListOf<URL>(), context))
//                val newDirs = arrayListOf<String>().apply { addAll(Kobalt.buildSourceDirs) }
//                newDirs.removeAll(currentDirs)
//                buildScriptInfo.includedBuildSourceDirs.add(IncludedBuildSourceDir(section.start - 1, newDirs))
//                println("*** ADDED DIRECTORIES " + newDirs)
//            }
//        }
//
//        //
//        // All the plug-ins are now in Plugins.dynamicPlugins, download them if they're not already
//        //
//        Plugins.dynamicPlugins.forEach {
//            pluginUrls.add(it.jarFile.get().toURI().toURL())
//        }
//    }

//    private fun generateJarFile(context: KobaltContext, sourceFiles: List<String>,
//            buildScriptJarFile: File, originalFile: BuildFile? = null) {
//        //
//        // Compile the jar file
//        //
//        val kotlinDeps = dependencyManager.calculateDependencies(null, context)
//        val deps: List<String> = kotlinDeps.map { it.jarFile.get().absolutePath }
//        val outputJar = File(buildScriptJarFile.absolutePath)
//        val saved = context.internalContext.noIncrementalKotlin
//        val result = kotlinCompilePrivate {
//            classpath(files.kobaltJar)
//            classpath(deps)
//            sourceFiles(sourceFiles)
//            output = outputJar
//            noIncrementalKotlin = true
//        }.compile(context = context)
//        if (! result.success) {
//            val org = originalFile?.realPath ?: sourceFiles.joinToString(",")
//            throw KobaltException("Couldn't compile $org:\n" + result.errorMessage)
//        }
//
//        context.internalContext.noIncrementalKotlin = saved
//    }
}

