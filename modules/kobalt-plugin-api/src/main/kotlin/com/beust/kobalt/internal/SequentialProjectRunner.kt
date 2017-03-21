package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.AsciiArt
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.ITask
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.ProjectBuildStatus
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.kobaltError
import com.google.common.collect.ListMultimap
import com.google.common.collect.TreeMultimap
import java.util.*

/**
 * Build the projects in parallel.
 *
 * The projects are sorted in topological order and then run by the DynamicGraphExecutor in a single thread.
 */
class SequentialProjectRunner(val tasksByNames: (Project) -> ListMultimap<String, ITask>,
        val dependsOn: TreeMultimap<String, String>,
        val reverseDependsOn: TreeMultimap<String, String>, val runBefore: TreeMultimap<String, String>,
        val runAfter: TreeMultimap<String, String>,
        val alwaysRunAfter: TreeMultimap<String, String>, val args: Args, val pluginInfo: PluginInfo)
                : BaseProjectRunner() {

    override fun runProjects(taskInfos: List<TaskManager.TaskInfo>, projects: List<Project>)
            : TaskManager.RunTargetResult {
        var result = TaskResult()
        val failedProjects = hashSetOf<String>()
        val messages = Collections.synchronizedList(arrayListOf<TaskManager.ProfilerInfo>())

        val context = Kobalt.context!!

        projects.forEach { project ->
            val projectName = project.name
            fun klog(level: Int, message: String) = context.logger.log(projectName, level, message)
            klog(1, AsciiArt.logBox("Building $projectName", indent = 5))

            // Does the current project depend on any failed projects?
            val fp = project.allProjectDependedOn().filter { failedProjects.contains(it.name) }.map(Project::name)

            if (fp.size > 0) {
                klog(2, "Marking project $projectName as skipped")
                failedProjects.add(project.name)
                runBuildListenersForProject(project, context, false, ProjectBuildStatus.SKIPPED)
                kobaltError("Not building project ${project.name} since it depends on failed "
                        + Strings.pluralize(fp.size, "project")
                        + " " + fp.joinToString(","))
            } else {
                runBuildListenersForProject(project, context, true)

                // There can be multiple tasks by the same name (e.g. PackagingPlugin and AndroidPlugin both
                // define "install"), so use a multimap
                val tasksByNames = tasksByNames(project)

                klog(3, "Tasks:")
                tasksByNames.keys().forEach {
                    klog(3, "  $it: " + tasksByNames.get(it))
                }

                val graph = createTaskGraph(project.name, taskInfos, tasksByNames,
                        dependsOn, reverseDependsOn, runBefore, runAfter, alwaysRunAfter,
                        ITask::name,
                        { task: ITask -> task.plugin.accept(project) })

                //
                // Now that we have a full graph, run it
                //
                klog(2, "About to run graph:\n  ${graph.dump()}  ")

                val factory = object : IThreadWorkerFactory<ITask> {
                    override fun createWorkers(nodes: Collection<ITask>)
                            = nodes.map { TaskWorker(listOf(it), args.dryRun, pluginInfo) }
                }

                val executor = DynamicGraphExecutor(graph, factory)
                val thisResult = executor.run()
                if (! thisResult.success) {
                    klog(2, "Marking project ${project.name} as failed")
                    failedProjects.add(project.name)
                }

                runBuildListenersForProject(project, context, false,
                        if (thisResult.success) ProjectBuildStatus.SUCCESS else ProjectBuildStatus.FAILED)

                if (result.success) {
                    result = thisResult
                }
            }
        }

        return TaskManager.RunTargetResult(result, messages)
    }
}
