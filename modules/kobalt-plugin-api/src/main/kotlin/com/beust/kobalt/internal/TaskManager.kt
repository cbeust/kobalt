package com.beust.kobalt.internal

import com.beust.kobalt.*
import com.beust.kobalt.api.DynamicTask
import com.beust.kobalt.api.IPlugin
import com.beust.kobalt.api.PluginTask
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.Args
import com.beust.kobalt.misc.log
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.TreeMultimap
import java.lang.reflect.Method
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class TaskManager @Inject constructor(val args: Args) {
    private val runBefore = TreeMultimap.create<String, String>()
    private val alwaysRunAfter = TreeMultimap.create<String, String>()

    /**
     * Called by plugins to indicate task dependencies defined at runtime. Keys depend on values.
     * Declare that `task1` depends on `task2`.
     *
     * Note: there is no runAfter on this class since a runAfter(a, b) in a task simply translates
     * to a runBefore(b, a) here.
     */
    fun runBefore(task1: String, task2: String) {
        runBefore.put(task1, task2)
    }

    fun alwaysRunAfter(task1: String, task2: String) {
        alwaysRunAfter.put(task1, task2)
    }

    data class TaskInfo(val id: String) {
        constructor(project: String, task: String) : this(project + ":" + task)

        val project: String?
            get() = if (id.contains(":")) id.split(":")[0] else null
        val taskName: String
            get() = if (id.contains(":")) id.split(":")[1] else id
        fun matches(projectName: String) = project == null || project == projectName
    }

    public fun runTargets(taskNames: List<String>, projects: List<Project>) : Int {
        var result = 0
        projects.forEach { project ->
            val projectName = project.name
            // There can be multiple tasks by the same name (e.g. PackagingPlugin and AndroidPlugin both
            // define "install"), so use a multimap
            val tasksByNames = ArrayListMultimap.create<String, PluginTask>()
            tasks.filter {
                it.project.name == project.name
            }.forEach {
                tasksByNames.put(it.name, it)
            }

            AsciiArt.logBox("Building project ${project.name}")

            log(3, "Tasks:")
            tasksByNames.keys().forEach {
                log(3, "  $it: " + tasksByNames.get(it))
            }
            val graph = DynamicGraph<PluginTask>()
            taskNames.forEach { taskName ->
                val ti = TaskInfo(taskName)
                if (! tasksByNames.keys().contains(ti.taskName)) {
                    throw KobaltException("Unknown task: $taskName")
                }

                if (ti.matches(projectName)) {
                    tasksByNames[ti.taskName].forEach { task ->
                        if (task != null && task.plugin.accept(project)) {
                            val reverseAfter = hashMapOf<String, String>()
                            alwaysRunAfter.keys().forEach { from ->
                                val tasks = alwaysRunAfter.get(from)
                                tasks.forEach {
                                    reverseAfter.put(it, from)
                                }
                            }

                            //
                            // If the current target is free, add it as a single node to the graph
                            //
                            val allFreeTasks = calculateFreeTasks(tasksByNames, reverseAfter)
                            val currentFreeTask = allFreeTasks.filter {
                                TaskInfo(projectName, it.name).taskName == task.name
                            }
                            if (currentFreeTask.size == 1) {
                                currentFreeTask[0].let {
                                    graph.addNode(it)
                                }
                            }

                            //
                            // Add the transitive closure of the current task as edges to the graph
                            //
                            val transitiveClosure = calculateTransitiveClosure(project, tasksByNames, ti)
                            transitiveClosure.forEach { pluginTask ->
                                val rb = runBefore.get(pluginTask.name)
                                rb.forEach {
                                    val tos = tasksByNames[it]
                                    if (tos != null && tos.size > 0) {
                                        tos.forEach { to ->
                                            graph.addEdge(pluginTask, to)
                                        }
                                    } else {
                                        log(2, "Couldn't find node $it: not applicable to project ${project.name}")
                                    }
                                }
                            }

                            //
                            // If any of the nodes in the graph has an "alwaysRunAfter", add that edge too
                            //
                            val allNodes = arrayListOf<PluginTask>()
                            allNodes.addAll(graph.nodes)
                            allNodes.forEach { node ->
                                val other = alwaysRunAfter.get(node.name)
                                other?.forEach { o ->
                                    tasksByNames[o]?.forEach {
                                        graph.addEdge(it, node)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //
            // Now that we have a full graph, run it
            //
            log(3, "About to run graph:\n  ${graph.dump()}  ")

            val factory = object : IThreadWorkerFactory<PluginTask> {
                override public fun createWorkers(nodes: List<PluginTask>): List<IWorker<PluginTask>> {
                    val thisResult = arrayListOf<IWorker<PluginTask>>()
                    nodes.forEach {
                        thisResult.add(TaskWorker(arrayListOf(it), args.dryRun))
                    }
                    return thisResult
                }
            }

            val executor = DynamicGraphExecutor(graph, factory)
            val thisResult = executor.run()
            if (result == 0) {
                result = thisResult
            }

        }
        return result
    }

    /**
     * Find the free tasks of the graph.
     */
    private fun calculateFreeTasks(tasksByNames: Multimap<String, PluginTask>, reverseAfter: HashMap<String, String>)
            : Collection<PluginTask> {
        val freeTaskMap = hashMapOf<String, PluginTask>()
        tasksByNames.keys().forEach {
            if (! runBefore.containsKey(it) && ! reverseAfter.containsKey(it)) {
                tasksByNames[it].forEach { t ->
                    freeTaskMap.put(it, t)
                }
            }
        }

        return freeTaskMap.values
    }

    /**
     * Find the transitive closure for the given TaskInfo
     */
    private fun calculateTransitiveClosure(project: Project, tasksByNames: Multimap<String, PluginTask>, ti: TaskInfo):
            HashSet<PluginTask> {
        log(3, "Processing ${ti.taskName}")

        val transitiveClosure = hashSetOf<PluginTask>()
        val seen = hashSetOf(ti.taskName)
        val toProcess = hashSetOf(ti)
        var done = false
        while (! done) {
            val newToProcess = hashSetOf<TaskInfo>()
            log(3, "toProcess size: " + toProcess.size)
            toProcess.forEach { target ->

                val currentTask = TaskInfo(project.name, target.taskName)
                val thisTask = tasksByNames[currentTask.taskName]
                if (thisTask != null) {
                    transitiveClosure.addAll(thisTask)
                    val dependencyNames = runBefore.get(currentTask.taskName)
                    dependencyNames.forEach { dependencyName ->
                        if (!seen.contains(dependencyName)) {
                            newToProcess.add(currentTask)
                            seen.add(dependencyName)
                        }
                    }

                    dependencyNames.forEach {
                        newToProcess.add(TaskInfo(project.name, it))
                    }
            } else {
                    log(2, "Couldn't find task ${currentTask.taskName}: not applicable to project ${project.name}")
                }
            }
            done = newToProcess.isEmpty()
            toProcess.clear()
            toProcess.addAll(newToProcess)
        }

        return transitiveClosure
    }

    /////
    // Manage the tasks
    //

    class StaticTask(val plugin: IPlugin, val method: Method, val taskAnnotation: Task)
    class PluginDynamicTask(val plugin: IPlugin, val task: DynamicTask)

    val tasks = arrayListOf<PluginTask>()
    val staticTasks = arrayListOf<StaticTask>()
    val dynamicTasks = arrayListOf<PluginDynamicTask>()

    /**
     * Turn all the static and dynamic tasks into plug-in tasks, which are then suitable to be executed.
     */
    fun computePluginTasks(plugins: List<IPlugin>, projects: List<Project>) {
        addStaticTasks(projects)
        addDynamicTasks(projects)
    }

    private fun addDynamicTasks(projects: List<Project>) {
        dynamicTasks.forEach { dynamicTask ->
            val task = dynamicTask.task
            projects.filter { dynamicTask.plugin.accept(it) }.forEach { project ->
                addTask(dynamicTask.plugin, project, task.name, task.description, task.runBefore, task.runAfter,
                        task.alwaysRunAfter, task.closure)
            }
        }
    }

    private fun addStaticTasks(projects: List<Project>) {
        staticTasks.forEach { staticTask ->
            val method = staticTask.method
            val annotation = staticTask.taskAnnotation

            val methodName = method.declaringClass.toString() + "." + method.name
            log(3, "    Found task:${annotation.name} method: $methodName")

            fun toTask(m: Method, project: Project, plugin: IPlugin): (Project) -> TaskResult {
                val result: (Project) -> TaskResult = {
                    m.invoke(plugin, project) as TaskResult
                }
                return result
            }

            val plugin = staticTask.plugin
            projects.filter { plugin.accept(it) }.forEach { project ->
                addStaticTask(plugin, project, staticTask.taskAnnotation, toTask(method, project, plugin))
            }
        }
    }

    private fun addStaticTask(plugin: IPlugin, project: Project, annotation: Task, task: (Project) -> TaskResult) {
        addTask(plugin, project, annotation.name, annotation.description, annotation.runBefore.toList(),
                annotation.runAfter.toList(), annotation.alwaysRunAfter.toList(), task)
    }

    fun addTask(plugin: IPlugin, project: Project, name: String, description: String = "",
            runBefore: List<String> = listOf<String>(),
            runAfter: List<String> = listOf<String>(),
            alwaysRunAfter: List<String> = listOf<String>(),
            task: (Project) -> TaskResult) {
        tasks.add(
                object : BasePluginTask(plugin, name, description, project) {
                    override fun call(): TaskResult2<PluginTask> {
                        val taskResult = task(project)
                        return TaskResult2(taskResult.success, taskResult.errorMessage, this)
                    }
                })
        runBefore.forEach { runBefore(it, name) }
        runAfter.forEach { runBefore(name, it) }
        alwaysRunAfter.forEach { alwaysRunAfter(it, name)}
    }

    //
    //
    /////

}

class TaskWorker(val tasks: List<PluginTask>, val dryRun: Boolean) : IWorker<PluginTask> {
//    override fun compareTo(other: IWorker2<PluginTask>): Int {
//        return priority.compareTo(other.priority)
//    }

    override fun call() : TaskResult2<PluginTask> {
        if (tasks.size > 0) {
            tasks[0].let {
                log(1, AsciiArt.taskColor(AsciiArt.horizontalSingleLine + " ${it.project.name}:${it.name}"))
            }
        }
        var success = true
        val errorMessages = arrayListOf<String>()
        tasks.forEach {
            val tr = if (dryRun) TaskResult() else it.call()
            success = success and tr.success
            if (tr.errorMessage != null) errorMessages.add(tr.errorMessage)
        }
        return TaskResult2(success, errorMessages.joinToString("\n"), tasks[0])
    }

//    override val timeOut : Long = 10000

    override val priority: Int = 0
}