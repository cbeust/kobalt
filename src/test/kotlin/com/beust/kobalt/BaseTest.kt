package com.beust.kobalt

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.KobaltPluginXml
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.build.SingleFileBuildSources
import com.beust.kobalt.misc.KFiles
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
        val projectDirectory = KFiles.fixSlashes(Files.createTempDirectory("kobaltTest").toFile())
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

        fun createBuildFile(projectDirectory: String) : File {
            val path = Paths.get(projectDirectory, "kobalt", "src")
            return File(path.toFile(), "Build.kt").apply {
                File(parent).mkdirs()
                deleteOnExit()
                writeText(buildFileText)
            }
        }

        val actualBuildFile = createBuildFile(projectDirectory)
        val tmpBuildFile = createBuildFile(Files.createTempDirectory("").toFile().absolutePath)

        val thisBuildFile = SingleFileBuildSources(tmpBuildFile)
//            , "Build.kt",
//                Paths.get(actualBuildFile.absolutePath))
        Kobalt.context?.log(2, "About to compile build file $thisBuildFile"
                + ".kobaltDir: " + KFiles.dotKobaltDir)
        args.apply {
            buildFile = actualBuildFile.absolutePath
            noIncremental = true
            noIncrementalKotlin = true
        }
        val jvmCompilerPlugin = Kobalt.findPlugin("JvmCompiler") as JvmCompilerPlugin
        val pluginInfo = PluginInfo(KobaltPluginXml(), null, null).apply {
            projectContributors.add(jvmCompilerPlugin)
        }
        return compilerFactory!!.create(thisBuildFile, pluginInfo)
                .compileBuildFiles(args, forceRecompile = true)
    }

    fun createTemporaryProjectDirectory() = KFiles.fixSlashes(Files.createTempDirectory("kobaltTest").toFile())

    fun createProject(projectInfo: ProjectInfo) : File {
        val root = Files.createTempDirectory("kobalt-test").toFile()

        fun createFile(root: File, f: String, text: String) : File {
            val file = File(root, f)
            file.parentFile.mkdirs()
            file.writeText(text)
            return file
        }

        createFile(root, "kobalt/src/Build.kt", projectInfo.buildFile.text(root.absolutePath))
        projectInfo.files.forEach {
            createFile(root, it.path, it.content)
        }
        return root
    }
}

class BuildFile(val imports: List<String>, val projectText: String) {
    fun text(projectDirectory: String) : String {
        val projectName = "p" + Math.abs(Random().nextInt())
        val bottom = """

            val $projectName = project {
                name = "$projectName"
                $projectText
            }

        """
//                .trimIndent()
        val buildFileText= """
            import com.beust.kobalt.*
            import com.beust.kobalt.api.*
""".trimIndent() +
        "\n" +
                imports.map { "import " + it }.joinToString("\n") + "\n" +
                bottom

        return buildFileText
    }
}
class ProjectFile(val path: String, val content: String)
class ProjectInfo(val buildFile: BuildFile, val files: List<ProjectFile>)