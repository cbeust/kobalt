package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.DynamicGraph
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.google.inject.Inject
import java.io.File
import java.nio.file.Paths

interface IProgressListener {
    /**
     * progress is an integer between 0 and 100 that represents the percentage.
     */
    fun onProgress(progress: Int? = null, message: String? = null)
}

class DependencyData @Inject constructor(val executors: KobaltExecutors, val dependencyManager: DependencyManager,
        val buildFileCompilerFactory: BuildFileCompiler.IFactory, val pluginInfo: PluginInfo,
        val taskManager: TaskManager) {

    fun dependenciesDataFor(buildFilePath: String, args: Args, progressListener: IProgressListener? = null,
            useGraph : Boolean = false): GetDependenciesData {
        val projectDatas = arrayListOf<ProjectData>()

        fun toDependencyData(d: IClasspathDependency, scope: String): DependencyData {
            val dep = dependencyManager.create(d.id)
            return DependencyData(d.id, scope, dep.jarFile.get().absolutePath)
        }

        fun allDeps(l: List<IClasspathDependency>, name: String) = dependencyManager.transitiveClosure(l,
                requiredBy = name)

        val buildFile = BuildFile(Paths.get(buildFilePath), "GetDependenciesCommand")
        val buildFileCompiler = buildFileCompilerFactory.create(listOf(buildFile), pluginInfo)
        val projectResult = buildFileCompiler.compileBuildFiles(args)

        val pluginDependencies = projectResult.pluginUrls.map { File(it.toURI()) }.map {
            FileDependency(it.absolutePath)
        }

        fun compileDependencies(project: Project, name: String): List<DependencyData> {
            val result =
                    (pluginDependencies +
                    allDeps(project.compileDependencies, name) +
                    allDeps(project.compileProvidedDependencies, name))
                .map { toDependencyData(it, "compile") }
            return result
        }

        fun toDependencyData2(scope: String, node: DynamicGraph.Companion.Node<IClasspathDependency>): DependencyData {
            val d = node.value
            val dep = dependencyManager.create(d.id)
            return DependencyData(d.id, scope, dep.jarFile.get().absolutePath,
                    node.children.map { toDependencyData2(scope, it) })
        }

        fun compileDependenciesGraph(project: Project, name: String): List<DependencyData> {
            val depLambda = { dep : IClasspathDependency -> dep.directDependencies() }
            val result =
                    (DynamicGraph.Companion.transitiveClosureGraph(pluginDependencies, depLambda) +
                    DynamicGraph.Companion.transitiveClosureGraph(project.compileDependencies, depLambda) +
                    DynamicGraph.Companion.transitiveClosureGraph(project.compileProvidedDependencies, depLambda))
                .map { toDependencyData2("compile", it)}
            return result
        }

        fun testDependencies(project: Project, name: String): List<DependencyData> {
            return allDeps(project.testDependencies, name).map { toDependencyData(it, "testCompile") }
        }

        fun testDependenciesGraph(project: Project, name: String): List<DependencyData> {
            val depLambda = { dep : IClasspathDependency -> dep.directDependencies() }
            return DynamicGraph.Companion.transitiveClosureGraph(project.testDependencies, depLambda)
                    .map { toDependencyData2("testCompile", it)}
        }

        val allTasks = hashSetOf<TaskData>()
        projectResult.projects.withIndex().forEach { wi ->
            val project = wi.value
            val name = project.name
            progressListener?.onProgress(message = "Synchronizing project ${project.name} "
                    + (wi.index + 1) + "/" + projectResult.projects.size)

            val dependentProjects = project.dependsOn.map { it.name }

            // Separate resource from source directories
            val sources = project.sourceDirectories.partition { KFiles.isResource(it) }
            val tests = project.sourceDirectoriesTest.partition { KFiles.isResource(it) }
            val projectTasks = taskManager.tasksByNames(project).values().map {
                TaskData(it.name, it.doc, it.group)
            }
            allTasks.addAll(projectTasks)
            val compileDependencies =
                if (useGraph) compileDependenciesGraph(project, project.name)
                else compileDependencies(project, project.name)
            val testDependencies =
                if (useGraph) testDependenciesGraph(project, project.name)
                else testDependencies(project, project.name)

            projectDatas.add(ProjectData(project.name, project.directory, dependentProjects,
                    compileDependencies, testDependencies,
                    sources.second.toSet(), tests.second.toSet(), sources.first.toSet(), tests.first.toSet(),
                    projectTasks))
        }
        return GetDependenciesData(projectDatas, allTasks, projectResult.taskResult.errorMessage)
    }

    /////
    // The JSON payloads that this command uses. The IDEA plug-in (and any client of the server) needs to
    // use these same classes.
    //

    class DependencyData(val id: String, val scope: String, val path: String,
            val children: List<DependencyData> = emptyList())
    data class TaskData(val name: String, val description: String, val group: String) {
        override fun toString() = name
    }

    class ProjectData(val name: String, val directory: String,
            val dependentProjects: List<String>,
            val compileDependencies: List<DependencyData>,
            val testDependencies: List<DependencyData>, val sourceDirs: Set<String>, val testDirs: Set<String>,
            val sourceResourceDirs: Set<String>, val testResourceDirs: Set<String>,
            val tasks: Collection<TaskData>)

    class GetDependenciesData(val projects: List<ProjectData> = emptyList(),
            val allTasks: Collection<TaskData> = emptySet(),
            val errorMessage: String?) {
        companion object {
            val NAME = "GetDependencies"
        }
    }
}
