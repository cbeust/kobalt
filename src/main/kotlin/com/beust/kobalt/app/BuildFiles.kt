package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.KobaltPluginXml
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.build.BuildSources
import com.beust.kobalt.misc.*
import com.google.inject.Inject
import java.io.File
import java.net.URL
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.regex.Pattern

/**
 * Parse all the files found in kobalt/src/ *kt, extract their buildScriptInfo blocks,
 * save the location where they appear (file, start/end).

 * Compile each of these buildScriptInfo separately, note which new build files they add
 * and at which location.

 * Go back over all the files from kobalt/src/ *kt, insert each new build file in it,
 * save it as a modified, concatenated big build file in .kobalt/build/Built.kt.

 * Compile .kobalt/build/Build.kt into buildScript.jar.
 *
 * And while doing all that, apply all the active profiles.
 */
class BuildFiles @Inject constructor(val factory: BuildFileCompiler.IFactory,
        val buildScriptUtil: BuildScriptUtil) {
    var containsProfiles = false
    val projects = arrayListOf<Project>()

    class BuildFileWithBuildScript(val file: File, val buildScriptInfo: BuildScriptInfo)

    class BuildFileParseResult(val projectRoot: String, val buildKt: File,
            val buildSourceDirectories: List<String>)

    /**
     * @return the new Build.kt
     */
    fun parseBuildFiles(projectDir: String, context: KobaltContext) : BuildFileParseResult {
        val profiles = Profiles(context)
        val bsiMap = hashMapOf<File, BuildFileWithBuildScript>()
        val newSourceDirs = arrayListOf<IncludedBuildSourceDir>()

        //
        // Create a map of File -> FileWithBuildScript
        //
        val filesWithBuildScript = parseBuildScriptInfos(projectDir, context, profiles)
        filesWithBuildScript.forEach {
            bsiMap.put(it.file, it)
        }

        //
        // Add any source directory we found
        //
        if (filesWithBuildScript.any()) {
            filesWithBuildScript.forEach { af ->
                val bsi = af.buildScriptInfo
                newSourceDirs.addAll(bsi.includedBuildSourceDirs)
            }
            log(2, "  Found buildScriptInfos: " + filesWithBuildScript)
        } else {
            log(2, "  No buildScriptInfos")
        }

        //
        // Go through all the build files and insert the content of included directories wherever appropriate
        //
        val imports = arrayListOf<String>()
        val code = arrayListOf<String>()
        val sourceDir = sourceDir(projectDir)
        findFiles(sourceDir, { it.name.endsWith(".kt") }).forEach { file ->
            code.add("\n// $file")
            val analyzedFile = bsiMap[file]
            val bsi = analyzedFile?.buildScriptInfo

            file.readLines().forEachIndexed { lineNumber, line ->
                if (bsi == null || ! bsi.isInSection(lineNumber)) {
                    //
                    // Not a buildScriptInfo section, just copy the line as is
                    //
                    profiles.correctProfileLine(line).let { pair ->
                        val cpl = pair.first
                        containsProfiles = containsProfiles or pair.second
                        (if (cpl.startsWith("import")) imports else code).add(cpl)
                    }
                } else {
                    //
                    // We're inside a buildScriptInfo section, see if it includes any buildSourceDirs
                    // and if it does, include these build files here
                    //
                        val isd = bsi.includedBuildSourceDirsForLine(lineNumber)
                    log(2, "  Skipping buildScript{} line $lineNumber from file $file")
                    if (isd.any()) {
                        // If we found any new buildSourceDirs, all all the files found in these directories
                        // to the big Build.kt
                        val allBuildFiles = isd.flatMap { findBuildSourceFiles(projectDir + File.separator + it) }
                        val sbf = includeFileContent(context, allBuildFiles, profiles)
                        imports.addAll(sbf.imports)
                        code.addAll(sbf.code)
                    }
                }
            }
        }

        //
        // Create the big Build.kt out of the imports and code we've found so far
        //
        val newBuildFile = with(File(KFiles.findBuildScriptDir(projectDir), "Build.kt")) {
            parentFile.mkdirs()
            val imp = arrayListOf<String>().apply {
                addAll(imports)
            }.toMutableSet().toMutableList()
            Collections.sort(imp)
            writeText(imp.joinToString("\n"))
            appendText(code.joinToString("\n"))
            this
        }

        val newDirs = listOf(File(BuildFiles.buildContentRoot(projectDir)).relativeTo(File(projectDir)).path) +
            newSourceDirs.flatMap{ it.dirs.map { BuildFiles.buildContentRoot(it)} }
        return BuildFileParseResult(projectDir, newBuildFile, newDirs)
    }

    class SplitBuildFile(val imports: List<String>, val code: List<String>, val containsProfiles: Boolean)

    private fun includeFileContent(context: KobaltContext, files: List<File>, profiles: Profiles) : SplitBuildFile {
        val imports = arrayListOf<String>()
        val code = arrayListOf<String>()

        files.forEach {
            code.add("// $it")
            val sbf = profiles.applyProfiles(it.readLines())
            containsProfiles = containsProfiles or sbf.containsProfiles
            imports.addAll(sbf.imports)
            code.addAll(sbf.code)
        }
        return SplitBuildFile(imports, code, containsProfiles)
    }

    companion object {
        val BUILD_SCRIPT_REGEXP: Pattern = Pattern.compile("^val.*buildScript.*\\{")
        val BLOCK_EXTRACTOR = BlockExtractor(BUILD_SCRIPT_REGEXP, '{', '}')

        /**
         * The content root for a build file module.
         */
        fun buildContentRoot(root: String) = root + File.separatorChar + "kobalt"
    }

    fun parseBuildScriptInfos(projectDir: String, context: KobaltContext, profiles: Profiles)
            : List<BuildFileWithBuildScript> {
        val root = sourceDir(projectDir)
        val files = findBuildSourceFiles(projectDir)
        val toProcess = arrayListOf<File>().apply { addAll(files) }

        // Parse each build file and associate it with a BuildScriptInfo if any buildScript{} is found
        val analyzedFiles = arrayListOf<BuildFileWithBuildScript>()
        toProcess.forEach { buildFile ->
            val splitBuildFile = profiles.applyProfiles(buildFile.readLines())
            containsProfiles = containsProfiles or splitBuildFile.containsProfiles
            val bsi = BLOCK_EXTRACTOR.extractBlock(buildFile, (splitBuildFile.imports + splitBuildFile.code))
            if (bsi != null) analyzedFiles.add(BuildFileWithBuildScript(buildFile, bsi))
        }

        // Run every buildScriptInfo section in its own source file
        var counter = 0
        analyzedFiles.forEach { af ->
            val buildScriptInfo = af.buildScriptInfo
            buildScriptInfo.sections.forEach { section ->

                //
                // Create a source file with just this buildScriptInfo{}
                //
                val bs = af.file.readLines().subList(section.start, section.end + 1)
                val source = (buildScriptInfo.imports + buildScriptInfo.topLines + bs).joinToString("\n")
                val sourceFile = Files.createTempFile(null, ".kt").toFile().apply {
                    writeText(source)
                }

                val buildScriptJarFile = File(KFiles.findBuildScriptDir(projectDir), "preBuildScript-$counter.jar").apply {
                    delete()
                }

                counter++

                //
                // Compile it to preBuildScript-xxx.jar
                //
                kobaltLog(2, "  Compiling buildScriptInfo $sourceFile to $buildScriptJarFile")
                val taskResult = factory.create(BuildSources(root), context.pluginInfo).maybeCompileBuildFile(context,
                        listOf(sourceFile.path),
                        buildScriptJarFile, emptyList<URL>(),
                        context.internalContext.forceRecompile,
                        containsProfiles)
                if (! taskResult.success) {
                    throw KobaltException("Couldn't compile $sourceFile: ${taskResult.errorMessage}")
                }

                log(2, "Created $buildScriptJarFile")

                //
                // Run preBuildScript.jar to initialize plugins and repos
                //
                val currentDirs = arrayListOf<String>().apply { addAll(Kobalt.buildSourceDirs) }
                buildScriptUtil.runBuildScriptJarFile(buildScriptJarFile, listOf<URL>(), context)
                val newDirs = arrayListOf<String>().apply { addAll(Kobalt.buildSourceDirs) }
                newDirs.removeAll(currentDirs)
                if (newDirs.any()) {
                    buildScriptInfo.addBuildSourceDir(IncludedBuildSourceDir(section.start, newDirs))
                }
            }

        }

        return analyzedFiles
    }

    private fun sourceDir(root: String) = File(KFiles.joinDir(buildContentRoot(root), "src"))

    private fun findFiles(file: File, accept: (File) -> Boolean) : List<File> {
        val result = arrayListOf<File>()

        // It's possible for no build file to be present (e.g. testing)
        if (file.exists()) {
            Files.walkFileTree(Paths.get(file.path), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                    if (accept(file.toFile())) result.add(file.toFile())
                    return FileVisitResult.CONTINUE
                }
            })
        }

        return result
    }

    private fun findBuildSourceFiles(root: String) : List<File> {
        val result = arrayListOf<File>()

        result.addAll(findFiles(sourceDir(root), { it.name.endsWith(".kt") }))
        return result
    }
}

fun main(argv: Array<String>) {
    val args = Args().apply {
        incrementalKotlin = false
    }
    val context = KobaltContext(args)
    KobaltLogger.LOG_LEVEL = 3
    context.pluginInfo = PluginInfo(KobaltPluginXml(), null, null)
    Kobalt.init(MainModule(args, KobaltSettings.readSettingsXml()))
    val bf = Kobalt.INJECTOR.getInstance(BuildFiles::class.java)
    bf.parseBuildFiles(homeDir("kotlin/klaxon/"), context)
}

