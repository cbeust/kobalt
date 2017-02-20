package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.DynamicGraph
import com.beust.kobalt.internal.GraphUtil
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.Versions
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.File
import java.nio.file.Paths

interface IProgressListener {
    /**
     * progress is an integer between 0 and 100 that represents the percentage.
     */
    fun onProgress(progress: Int? = null, message: String? = null)
}

class RemoteDependencyData @Inject constructor(val executors: KobaltExecutors, val dependencyManager: DependencyManager,
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
            DependencyData(it.name, "compile", it.absolutePath)
        }

        fun compileDependencies(project: Project, name: String): List<DependencyData> {
            val result =
                    (allDeps(project.compileDependencies, name) +
                    allDeps(project.compileProvidedDependencies, name))
                .map { toDependencyData(it, "compile") }
            return result
        }

        fun toDependencyData2(scope: String, node: DynamicGraph.Companion.Node<IClasspathDependency>): DependencyData {
            val d = node.value
            val dep = dependencyManager.create(d.id)
            return DependencyData(d.id, scope, dep.jarFile.get().absolutePath,
                    children = node.children.map { toDependencyData2(scope, it) })
        }

        fun dependencyFilter(excluded: List<IClasspathDependency>) = { dep: IClasspathDependency ->
            if (excluded.contains(dep)) {
                log(2, "  Excluding $dep")
            }
            ! excluded.contains(dep) && ! dep.optional
        }

        fun compileDependenciesGraph(project: Project, name: String): List<DependencyData> {
            val depLambda = IClasspathDependency::directDependencies
            val resultDep =
                    (DynamicGraph.Companion.transitiveClosureGraph(project.compileDependencies, depLambda,
                            dependencyFilter(project.excludedDependencies)) +
                    DynamicGraph.Companion.transitiveClosureGraph(project.compileProvidedDependencies, depLambda,
                            dependencyFilter(project.excludedDependencies)))
            val result = resultDep
                .map { toDependencyData2("compile", it)}

            fun mapOfLatestVersions(l: List<DependencyData>) : Map<String, String> {
                fun p(l: List<DependencyData>, latestVersions: java.util.HashMap<String, String>) {
                    l.forEach {
                        val mid = dependencyManager.create(it.id)
                        if (mid.isMaven) {
                            val shortId = mid.shortId
                            val currentLatest = latestVersions[shortId]
                            if (currentLatest == null) latestVersions[shortId] = mid.version!!
                            else mid.version?.let { v ->
                                if (Versions.toLongVersion(currentLatest) < Versions.toLongVersion(v)) {
                                    latestVersions[shortId] = v
                                }
                            }
                        }
                        p(it.children, latestVersions)
                    }
                }
                val result = hashMapOf<String, String>()
                p(l, result)
                return result
            }
            val map = mapOfLatestVersions(result)
            GraphUtil.map(result, { d: DependencyData -> d.children },
                    {d: DependencyData ->
                        val mid = dependencyManager.create(d.id)
                        if (mid.isMaven) {
                            val version = map[mid.shortId]
                            d.isLatest = version == mid.version
                        }
                    })
            return result
        }

        fun testDependencies(project: Project, name: String): List<DependencyData> {
            return allDeps(project.testDependencies, name).map { toDependencyData(it, "testCompile") }
        }

        fun testDependenciesGraph(project: Project, name: String): List<DependencyData> {
            val depLambda = IClasspathDependency::directDependencies
            return DynamicGraph.Companion.transitiveClosureGraph(project.testDependencies, depLambda,
                    dependencyFilter(project.excludedDependencies))
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
            fun partition(project: Project, dirs: Collection<String>)
                    = dirs.filter { File(project.directory, it).exists() }
                    .partition { KFiles.isResource(it) }
            val sources = partition(project, project.sourceDirectories)
            val tests = partition(project, project.sourceDirectoriesTest)

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

        //
        // Debugging
        //
        log(2, "Returning dependencies:")

        projectDatas.forEach {
            log(2, "  Project: " + it.name)
            GraphUtil.displayGraph(it.compileDependencies,
                    {dd: DependencyData -> dd.children },
                    {dd: DependencyData, indent: String ->
                        println("    " + indent + dd.id + " " + (if (! dd.isLatest) "(old)" else ""))
                    })
        }

        return GetDependenciesData(projectDatas, allTasks, pluginDependencies,
                projectResult.taskResult.errorMessage)
    }

    /////
    // The JSON payloads that this command uses. The IDEA plug-in (and any client of the server) needs to
    // use these same classes.
    //

    class DependencyData(val id: String, val scope: String, val path: String, var isLatest: Boolean = true,
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
            val pluginDependencies: List<DependencyData> = emptyList(),
            val errorMessage: String?) {
        companion object {
            val NAME = "GetDependencies"
        }
    }
}
