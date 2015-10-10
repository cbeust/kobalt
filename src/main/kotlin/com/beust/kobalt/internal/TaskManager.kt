package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.PluginTask
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.maven.KobaltException
import com.beust.kobalt.plugins
import com.google.common.collect.HashMultimap
import com.google.common.collect.TreeMultimap
import org.jetbrains.kotlin.cfg.pseudocode.or
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class TaskManager @Inject constructor(val plugins: Plugins, val args: Args) : KobaltLogger {
    private val runBefore = TreeMultimap.create<String, String>()
    private val runAfter = TreeMultimap.create<String, String>()
    private val wrapAfter = TreeMultimap.create<String, String>()

    /**
     * Called by plugins to indicate task dependencies defined at runtime. Keys depend on values.
     * Declare that `task1` depends on `task2`.
     */
    fun runBefore(task1: String, task2: String) {
        runBefore.put(task1, task2)
    }

    fun runAfter(task1: String, task2: String) {
        runAfter.put(task1, task2)
    }

    fun wrapAfter(task1: String, task2: String) {
        wrapAfter.put(task1, task2)
    }

    data class TaskInfo(val id: String) {
        constructor(project: String, task: String) : this(project + ":" + task)

        val project: String?
            get() = if (id.contains(":")) id.split(":").get(0) else null
        val task: String
            get() = if (id.contains(":")) id.split(":").get(1) else id
        fun matches(projectName: String) = project == null || project == projectName
    }

    public fun runTargets(targets: List<String>, projects: List<Project>) {
        val tasksAlreadyRun = hashSetOf<String>()
        projects.forEach { project ->
            val projectName = project.name!!
            val tasksByNames = hashMapOf<String, PluginTask>()
            plugins.allTasks.filter {
                it.project.name == project.name
            }.forEach {
                tasksByNames.put(it.name, it)
            }

            log(1, "")
            log(1, "                   Building project ${project.name}")
            log(1, "")

            targets.forEach { target ->
                tasksAlreadyRun.add(TaskInfo(projectName, target).id)

                val graph = DynamicGraph<PluginTask>()

                val ti = TaskInfo(target)
                if (ti.matches(projectName)) {
                    val task = tasksByNames.get(ti.task)
                    if (task != null && task.plugin.accept(project)) {
                        //
                        // Add free tasks as nodes to the graph
                        //
                        calculateFreeTasks(tasksByNames).forEach {
                            val thisTaskInfo = TaskInfo(projectName, it.name)
                            if (! tasksAlreadyRun.contains(thisTaskInfo.id)) {
                                graph.addNode(it)
                                tasksAlreadyRun.add(thisTaskInfo.id)
                            }
                        }

                        //
                        // Add the transitive closure of the current task as edges to the graph
                        //
                        calculateTransitiveClosure(project, tasksByNames, ti, task).forEach { pluginTask ->
                            val rb = runBefore.get(pluginTask.name)
                            rb.forEach {
                                val to = tasksByNames.get(it)
                                if (to != null) {
                                    val taskInfo = TaskInfo(projectName, to.name)
                                    if (! tasksAlreadyRun.contains(taskInfo.id)) {
                                        graph.addEdge(pluginTask, to)
                                        tasksAlreadyRun.add(taskInfo.id)
                                    }
                                } else {
                                    throw KobaltException("Should have found $it")
                                }
                            }
                        }
                    }

                    //
                    // Now that we have a full graph, run it
                    //
                    val factory = object : IThreadWorkerFactory<PluginTask> {
                        override public fun createWorkers(nodes: List<PluginTask>): List<IWorker<PluginTask>> {
                            val result = arrayListOf<IWorker<PluginTask>>()
                            nodes.forEach {
                                result.add(TaskWorker(arrayListOf(it), args.dryRun))
                            }
                            return result
                        }
                    }

                    val executor = DynamicGraphExecutor(graph, factory)
                    executor.run()
                }
            }
        }
    }

    /**
     * Find the free tasks of the graph.
     */
    private fun calculateFreeTasks(tasksByNames: Map<String, PluginTask>): Collection<PluginTask> {
        val freeTaskMap = hashMapOf<String, PluginTask>()
        tasksByNames.keySet().forEach {
            if (! runBefore.containsKey(it)) {
                freeTaskMap.put(it, tasksByNames.get(it)!!)
            }
        }

        log(2, "Free tasks: ${freeTaskMap.keySet()}")
        log(2, "Dependent tasks:")
        runBefore.keySet().forEach { t ->
            log(2, "  ${t} -> ${runBefore.get(t)}}")
        }

        return freeTaskMap.values()
    }

    /**
     * Find the transitive closure for the given TaskInfo
     */
    private fun calculateTransitiveClosure(project: Project, tasksByNames: Map<String, PluginTask>, ti: TaskInfo,
            task: PluginTask): HashSet<PluginTask> {
        log(3, "Processing ${ti.task}")

        val transitiveClosure = hashSetOf<PluginTask>()
        val seen = hashSetOf(ti.task)
        val toProcess = hashSetOf(ti)
        var done = false
        while (! done) {
            val newToProcess = hashSetOf<TaskInfo>()
            log(3, "toProcess size: " + toProcess.size())
            toProcess.forEach { target ->

                wrapAfter.get(ti.id).let {
                    val tasks = tasksByNames.get(it)
                    if (tasks != null) {
                        tasks.forEach {
                            newToProcess.add(TaskInfo(project.name!!, task!!.name))
                        }
                }

                val currentTask = TaskInfo(project.name!!, target.task)
                transitiveClosure.add(tasksByNames.get(currentTask.task)!!)
                val task = tasksByNames.get(target.task)
                if (task == null) {
                    throw KobaltException("Unknown task: ${target}")
                } else {
                    val dependencyNames = runBefore.get(task.name)
                    dependencyNames.forEach { dependencyName ->
                        if (! seen.contains(dependencyName)) {
                            newToProcess.add(currentTask)
                            seen.add(dependencyName)
                        }
                    }

                    runBefore.get(task.name).forEach {
                        newToProcess.add(TaskInfo(project.name!!, it))
                    }
                }
            }
            done = newToProcess.isEmpty()
            toProcess.clear()
            toProcess.addAll(newToProcess)
        }

        return transitiveClosure
    }
}

class TaskWorker(val tasks: List<PluginTask>, val dryRun: Boolean) : IWorker<PluginTask>, KobaltLogger {
//    override fun compareTo(other: IWorker2<PluginTask>): Int {
//        return priority.compareTo(other.priority)
//    }

    override fun call() : TaskResult2<PluginTask> {
        if (tasks.size() > 0) {
            tasks.get(0).let {
                log(1, "========== ${it.project.name}:${it.name}")
            }
        }
        var success = true
        tasks.forEach {
            val tr = if (dryRun) TaskResult() else it.call()
            success = success and tr.success
        }
        return TaskResult2(success, tasks.get(0))
    }

//    override val timeOut : Long = 10000

    override val priority: Int = 0
}