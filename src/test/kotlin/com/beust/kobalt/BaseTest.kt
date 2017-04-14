package com.beust.kobalt

import com.beust.jcommander.JCommander
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
import org.testng.annotations.Guice
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@Guice(modules = arrayOf(TestModule::class))
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

    class ProjectDescription(val file: File, val name: String, val version: String)

    fun createProject(projectInfo: ProjectInfo) : ProjectDescription {
        val root = Files.createTempDirectory("kobalt-test").toFile()
        val projectName = "p" + Math.abs(Random().nextInt())
        val version = "1.0"

        fun createFile(root: File, f: String, text: String) = File(root, f).apply {
            parentFile.mkdirs()
            writeText(text)
        }

        createFile(root, "kobalt/src/Build.kt",
                projectInfo.buildFile.text(KFiles.fixSlashes(root.absolutePath), projectName, version))

        projectInfo.files.forEach {
            createFile(root, it.path, it.content)
        }
        return ProjectDescription(root, projectName, version)
    }

    class LaunchProjectResult(val projectInfo: ProjectInfo, val projectDescription: ProjectDescription,
            val result: Int)

    fun launchProject(projectInfo: ProjectInfo, commandLine: Array<String>) : LaunchProjectResult {
        val project = createProject(projectInfo)
        println("Project: $project")
        val main = Kobalt.INJECTOR.getInstance(Main::class.java)
        val args = Args()
        val jc = JCommander(args).apply { parse(*commandLine) }
        args.buildFile = KFiles.fixSlashes(project.file.absolutePath) + "/kobalt/src/Build.kt"
        val result = Main.launchMain(main, jc, args, arrayOf("assemble"))
        return LaunchProjectResult(projectInfo, project, result)
    }
}

class BuildFile(val imports: List<String>, val projectText: String) {
    fun text(projectDirectory: String, projectName: String, version: String) : String {
        val bottom = """

            val $projectName = project {
                name = "$projectName"
                version = "$version"
                directory = "$projectDirectory"
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