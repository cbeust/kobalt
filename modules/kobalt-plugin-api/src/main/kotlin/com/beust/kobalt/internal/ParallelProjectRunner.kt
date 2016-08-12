package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.AsciiArt
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.ITask
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.ProjectBuildStatus
import com.beust.kobalt.misc.kobaltLog
import com.google.common.collect.ListMultimap
import com.google.common.collect.TreeMultimap
import java.util.concurrent.Callable

/**
 * Build the projects in parallel.
 *
 * The projects are sorted in topological order and then run by the DynamicGraphExecutor in background threads
 * wherever appropriate. Inside a project, all the tasks are run sequentially.
 */
class ParallelProjectRunner(val tasksByNames: (Project) -> ListMultimap<String, ITask>,
        val dependsOn: TreeMultimap<String, String>,
        val reverseDependsOn: TreeMultimap<String, String>, val runBefore: TreeMultimap<String, String>,
        val runAfter: TreeMultimap<String, String>,
        val alwaysRunAfter: TreeMultimap<String, String>, val args: Args, val pluginInfo: PluginInfo,
        val logger: ParallelLogger)
            : BaseProjectRunner() {
    override fun runProjects(taskInfos: List<TaskManager.TaskInfo>, projects: List<Project>)
            : TaskManager .RunTargetResult {
        class ProjectTask(val project: Project, val dryRun: Boolean) : Callable<TaskResult2<ProjectTask>> {
            override fun toString() = "[ProjectTask " + project.name + "]"
            override fun hashCode() = project.hashCode()
            override fun equals(other: Any?) : Boolean =
                    if (other is ProjectTask) other.project.name == project.name
                    else false

            override fun call(): TaskResult2<ProjectTask> {
                val context = Kobalt.context!!
                runBuildListenersForProject(project, context, true)
                val tasksByNames = tasksByNames(project)
                val graph = createTaskGraph(project.name, taskInfos, tasksByNames,
                        dependsOn, reverseDependsOn, runBefore, runAfter, alwaysRunAfter,
                        ITask::name,
                        { task: ITask -> task.plugin.accept(project) })
                var lastResult = TaskResult()
                logger.onProjectStarted(project.name)
                context.logger.log(project.name, 1, AsciiArt.logBox("Building ${project.name}", indent = 5))
                while (graph.freeNodes.any()) {
                    val toProcess = graph.freeNodes
                    toProcess.forEach { node ->
                        val tasks = tasksByNames[node.name]
                        tasks.forEach { task ->

                            runBuildListenersForTask(project, context, task.name, start = true)
                            logger.log(project.name, 1,
                                    AsciiArt.taskColor(AsciiArt.horizontalSingleLine + " ${project.name}:${task.name}"))
                            val thisResult = if (dryRun) TaskResult2(true, null, task) else task.call()
                            if (lastResult.success) {
                                lastResult = thisResult
                            }
                            runBuildListenersForTask(project, context, task.name, start = false,
                                    success = thisResult.success)
                        }
                    }
                    graph.freeNodes.forEach { graph.removeNode(it) }
                }

                logger.onProjectStopped(project.name)
                runBuildListenersForProject(project, context, false,
                        if (lastResult.success) ProjectBuildStatus.SUCCESS else ProjectBuildStatus.FAILED)

                return TaskResult2(lastResult.success, lastResult.errorMessage, this)
            }

        }

        val factory = object : IThreadWorkerFactory<ProjectTask> {
            override fun createWorkers(nodes: Collection<ProjectTask>): List<IWorker<ProjectTask>> {
                val result = nodes.map { it ->
                    object: IWorker<ProjectTask> {
                        override val priority: Int get() = 0
                        override val name: String get() = it.project.name
                        override fun call(): TaskResult2<ProjectTask> {
                            val tr = it.call()
                            return tr
                        }

                    }
                }
                return result
            }
        }

        val projectGraph = DynamicGraph<ProjectTask>().apply {
            projects.forEach { project ->
                addNode(ProjectTask(project, args.dryRun))
                project.dependsOn.forEach {
                    addEdge(ProjectTask(project, args.dryRun), ProjectTask(it, args.dryRun))
                }
            }
        }

        val executor = DynamicGraphExecutor(projectGraph, factory, 5)
        kobaltLog(1, "Parallel build starting")
        val taskResult = executor.run()

        logger.shutdown()

        if (! args.sequential) {
            executor.dumpHistory()
        }
        return TaskManager.RunTargetResult(taskResult, emptyList())
    }
}
