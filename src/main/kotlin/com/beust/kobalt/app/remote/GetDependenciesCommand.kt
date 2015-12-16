package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.internal.remote.CommandData
import com.beust.kobalt.internal.remote.ICommand
import com.beust.kobalt.internal.remote.ICommandSender
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.net.URL
import java.nio.file.Paths
import javax.inject.Inject

/**
 * This command returns the list of dependencies for the given buildFile.
 * Payload:
 * { "name" : "getDependencies", "buildFile": "/Users/beust/kotlin/kobalt/kobalt/src/Build.kt" }
 * The response is a GetDependenciesData.
 */
class GetDependenciesCommand @Inject constructor(val executors: KobaltExecutors,
        val buildFileCompilerFactory: BuildFileCompiler.IFactory, val args: Args,
        val dependencyManager: DependencyManager, val pluginInfo: PluginInfo) : ICommand {
    override val name = "getDependencies"
    override fun run(sender: ICommandSender, received: JsonObject) {
        val buildFile = BuildFile(Paths.get(received.get("buildFile").asString), "GetDependenciesCommand")
        val buildFileCompiler = buildFileCompilerFactory.create(listOf(buildFile), pluginInfo)
        val projects = buildFileCompiler.compileBuildFiles(args)
        sender.sendData(toData(projects, buildFileCompiler.parsedBuildFiles.flatMap { it.pluginUrls }))
    }

    private fun toData(projects: List<Project>, pluginUrls: List<URL>) : CommandData {
        val projectDatas = arrayListOf<ProjectData>()
        val executor = executors.miscExecutor

        fun toDependencyData(d: IClasspathDependency, scope: String) : DependencyData {
            val dep = MavenDependency.create(d.id, executor)
            return DependencyData(d.id, scope, dep.jarFile.get().absolutePath)
        }

        fun allDeps(l: List<IClasspathDependency>) = dependencyManager.transitiveClosure(l)

        val pluginDependencies = pluginUrls.map { File(it.toURI()) }.map { FileDependency(it.absolutePath) }
        projects.forEach { project ->
            val allDependencies =
                pluginDependencies.map { toDependencyData(it, "compile")} +
                allDeps(project.compileDependencies).map { toDependencyData(it, "compile") } +
                allDeps(project.compileProvidedDependencies).map { toDependencyData(it, "provided") } +
                allDeps(project.compileRuntimeDependencies).map { toDependencyData(it, "runtime") } +
                allDeps(project.testDependencies).map { toDependencyData(it, "testCompile") } +
                allDeps(project.testProvidedDependencies).map { toDependencyData(it, "testProvided") }

            projectDatas.add(ProjectData(project.name, project.directory, allDependencies,
                    project.sourceDirectories, project.sourceDirectoriesTest))
        }
        log(1, "Returning BuildScriptInfo")
        val result = toCommandData(Gson().toJson(GetDependenciesData(projectDatas)))
        log(2, "  $result")
        return result
    }

    /////
    // The JSON payloads that this command uses
    //

    class DependencyData(val id: String, val scope: String, val path: String)

    class ProjectData(val name: String, val directory: String, val dependencies: List<DependencyData>,
            val sourceDirs: Set<String>, val testDirs: Set<String>)

    class GetDependenciesData(val projects: List<ProjectData>)
}