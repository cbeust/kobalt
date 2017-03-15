package com.beust.kobalt

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.KobaltPluginXml
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.misc.log
import org.testng.annotations.BeforeClass
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

open class BaseTest(val compilerFactory: BuildFileCompiler.IFactory? = null) {
    @BeforeClass
    fun bc() {
        Kobalt.init(TestModule())
    }

    /**
     * Compile a single project. This function takes care of generating a random project
     * name and variable to contain it, so that multiple tests don't interfere with each
     * other when they attempt to class load the resulting build jar file.
     */
    fun compileSingleProject(projectText: String, args: Args = Args()) : Project {
        val projectName = "p" + Math.abs(Random().nextInt())
        val projectDirectory = Files.createTempDirectory("kobaltTest").toFile().path

        val buildFileText= """
            import com.beust.kobalt.*
            import com.beust.kobalt.api.*
            val $projectName = project {
                name = "$projectName"
                directory = "$projectDirectory"
                $projectText
            }
        """.trim()

        args.noIncremental = true
        val projectResults = compileBuildFile(projectDirectory, buildFileText, args)
        val result = projectResults.projects.firstOrNull { it.name == projectName }
        if (result == null) {
            throw IllegalArgumentException("Couldn't find project named $projectName in "
                    + projectResults.projects.map { it.name }.joinToString(", ", "[", "]"))
        } else {
            return result
        }
    }

    /**
     * Compile an entire build file, possibly containing multiple projects. Callers of this method
     * should preferably use random names for the projects defined in their build file to avoid
     * interfering with other tests.
     */
    fun compileBuildFile(projectDirectory: String, buildFileText: String, args: Args = Args())
            : BuildFileCompiler .FindProjectResult {
        val actualBuildFilePath = Paths.get(projectDirectory, "kobalt", "src")
        val actualBuildFile = File(actualBuildFilePath.toFile(), "Build.kt").apply {
            File(parent).mkdirs()
            deleteOnExit()
            writeText(buildFileText)
        }
        val tmpBuildFilePath = Paths.get(Files.createTempDirectory("").toFile().absolutePath, "kobalt", "src")
        val tmpBuildFile = File(tmpBuildFilePath.toFile(), "Build.kt").apply {
            File(parent).mkdirs()
            deleteOnExit()
            writeText(buildFileText)
        }

        val thisBuildFile = BuildFile(Paths.get(tmpBuildFile.absolutePath), "Build.kt",
                Paths.get(actualBuildFile.absolutePath))
        Kobalt.context?.log(2, "About to compile build file "
                + thisBuildFile.path + " " + thisBuildFile.realPath
                + ".kobaltDir: " + thisBuildFile.dotKobaltDir)
        args.apply {
            buildFile = actualBuildFile.absolutePath
            noIncremental = true
            noIncrementalKotlin = true
        }
        val jvmCompilerPlugin = Kobalt.findPlugin("JvmCompiler") as JvmCompilerPlugin
        val pluginInfo = PluginInfo(KobaltPluginXml(), null, null).apply {
            projectContributors.add(jvmCompilerPlugin)
        }
        return compilerFactory!!.create(listOf(thisBuildFile), pluginInfo).compileBuildFiles(args,
                forceRecompile = true)
    }
}