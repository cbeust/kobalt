package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.ProjectDescription
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
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
    fun dependenciesDataFor(buildFilePath: String, args: Args, progressListener: IProgressListener? = null)
            : GetDependenciesData {
        val projectDatas = arrayListOf<ProjectData>()

        fun toDependencyData(d: IClasspathDependency, scope: String): DependencyData {
            val dep = dependencyManager.create(d.id)
            return DependencyData(d.id, scope, dep.jarFile.get().absolutePath)
        }

        fun allDeps(l: List<IClasspathDependency>) = dependencyManager.transitiveClosure(l)

        val buildFile = BuildFile(Paths.get(buildFilePath), "GetDependenciesCommand")
        val buildFileCompiler = buildFileCompilerFactory.create(listOf(buildFile), pluginInfo)
        val projectResult = buildFileCompiler.compileBuildFiles(args)

        val pluginDependencies = projectResult.pluginUrls.map { File(it.toURI()) }.map {
            FileDependency(it.absolutePath)
        }

        val allTasks = hashSetOf<TaskData>()
        projectResult.projects.withIndex().forEach { wi ->
            val project = wi.value
            progressListener?.onProgress(message = "Synchronizing project ${project.name} "
                    + (wi.index + 1) + "/" + projectResult.projects.size)
            val compileDependencies = pluginDependencies.map { toDependencyData(it, "compile") } +
                    allDeps(project.compileDependencies).map { toDependencyData(it, "compile") } +
                    allDeps(project.compileProvidedDependencies).map { toDependencyData(it, "compile") }
            val testDependencies = allDeps(project.testDependencies).map { toDependencyData(it, "testCompile") }

            val pd = project.projectProperties.get(JvmCompilerPlugin.DEPENDENT_PROJECTS)
            val dependentProjects = if (pd != null) {
                    @Suppress("UNCHECKED_CAST")
                    (pd as List<ProjectDescription>).filter { it.project.name == project.name }.flatMap {
                        it.dependsOn.map { it.name }
                    }
                } else {
                    emptyList()
                }

            // Separate resource from source directories
            val sources = project.sourceDirectories.partition { KFiles.isResource(it) }
            val tests = project.sourceDirectoriesTest.partition { KFiles.isResource(it) }
            val projectTasks = taskManager.tasksByNames(project).values().map {
                TaskData(it.name, it.doc, it.group)
            }
            allTasks.addAll(projectTasks)
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

    class DependencyData(val id: String, val scope: String, val path: String)
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
