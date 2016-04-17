package com.beust.kobalt.internal

import com.beust.kobalt.*
import com.beust.kobalt.api.DynamicTask
import com.beust.kobalt.api.IPlugin
import com.beust.kobalt.api.PluginTask
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.benchmarkMillis
import com.beust.kobalt.misc.kobaltError
import com.beust.kobalt.misc.log
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.TreeMultimap
import java.lang.reflect.Method
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class TaskManager @Inject constructor(val args: Args,
        val incrementalManagerFactory: IncrementalManager.IFactory) {
    private val runBefore = TreeMultimap.create<String, String>()
    private val runAfter = TreeMultimap.create<String, String>()
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

    fun runAfter(task1: String, task2: String) {
        runAfter.put(task1, task2)
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

    class RunTargetResult(val exitCode: Int, val messages: List<String>)

    public fun runTargets(taskNames: List<String>, projects: List<Project>) : RunTargetResult {
        var result = 0
        val failedProjects = hashSetOf<String>()
        val messages = Collections.synchronizedList(arrayListOf<String>())
        projects.forEach { project ->
            AsciiArt.logBox("Building ${project.name}")

            // Does the current project depend on any failed projects?
            val fp = project.projectExtra.dependsOn.filter {
                failedProjects.contains(it.name)
            }.map {
                it.name
            }

            if (fp.size > 0) {
                log(2, "Marking project ${project.name} as skipped")
                failedProjects.add(project.name)
                kobaltError("Not building project ${project.name} since it depends on failed "
                        + Strings.pluralize(fp.size, "project")
                        + " " + fp.joinToString(","))
            } else {
                // There can be multiple tasks by the same name (e.g. PackagingPlugin and AndroidPlugin both
                // define "install"), so use a multimap
                val tasksByNames = ArrayListMultimap.create<String, PluginTask>()
                annotationTasks.filter {
                    it.project.name == project.name
                }.forEach {
                    tasksByNames.put(it.name, it)
                }

                log(3, "Tasks:")
                tasksByNames.keys().forEach {
                    log(3, "  $it: " + tasksByNames.get(it))
                }

                val graph = createGraph(project.name, taskNames, tasksByNames,
                        runBefore, runAfter, alwaysRunAfter,
                        { task: PluginTask -> task.name },
                        { task: PluginTask -> task.plugin.accept(project) })

                //
                // Now that we have a full graph, run it
                //
                log(2, "About to run graph:\n  ${graph.dump()}  ")

                val factory = object : IThreadWorkerFactory2<PluginTask> {
                    override fun createWorkers(nodes: Collection<PluginTask>)
                        = nodes.map { TaskWorker(listOf(it), args.dryRun, messages) }
                }

                val executor = DGExecutor(graph, factory)
                val thisResult = executor.run()
                if (thisResult != 0) {
                    log(2, "Marking project ${project.name} as failed")
                    failedProjects.add(project.name)
                }
                if (result == 0) {
                    result = thisResult
                }
            }
        }
        return RunTargetResult(result, messages)
    }

    @VisibleForTesting
    fun <T> createGraph(projectName: String, taskNames: List<String>, dependencies: Multimap<String, T>,
            runBefore: TreeMultimap<String, String>,
            runAfter: TreeMultimap<String, String>,
            alwaysRunAfter: TreeMultimap<String, String>,
            toName: (T) -> String,
            accept: (T) -> Boolean):
            DG<T> {
        val graph = DG<T>()
        taskNames.forEach { taskName ->
            val ti = TaskInfo(taskName)
            if (!dependencies.keys().contains(ti.taskName)) {
                throw KobaltException("Unknown task: $taskName")
            }

            if (ti.matches(projectName)) {
                dependencies[ti.taskName].forEach { task ->
                    if (task != null && accept(task)) {
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
                        val allFreeTasks = calculateFreeTasks(dependencies, reverseAfter)
                        val currentFreeTask = allFreeTasks.filter {
                            TaskInfo(projectName, toName(it)).taskName == toName(task)
                        }
                        if (currentFreeTask.size == 1) {
                            currentFreeTask[0].let {
                                graph.addNode(it)
                            }
                        }

                        //
                        // Add the transitive closure of the current task as edges to the graph
                        //
                        val transitiveClosure = calculateTransitiveClosure(projectName, dependencies, ti)
                        transitiveClosure.forEach { pluginTask ->
                            val rb = runBefore.get(toName(pluginTask))
                            rb.forEach {
                                val tos = dependencies[it]
                                if (tos != null && tos.size > 0) {
                                    tos.forEach { to ->
                                        graph.addEdge(pluginTask, to)
                                    }
                                } else {
                                    log(2, "Couldn't find node $it: not applicable to project $projectName")
                                }
                            }
                        }

                        //
                        // runAfter nodes are run only if they are explicitly requested
                        //
                        arrayListOf<T>().let { allNodes ->
                            allNodes.addAll(graph.values)
                            allNodes.forEach { node ->
                                val nodeName = toName(node)
                                if (taskNames.contains(nodeName)) {
                                    val ra = runAfter[nodeName]
                                    ra?.forEach { o ->
                                        dependencies[o]?.forEach {
                                            if (taskNames.contains(toName(it))) {
                                                graph.addEdge(node, it)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        //
                        // If any of the nodes in the graph has an "alwaysRunAfter", add that edge too
                        //
                        arrayListOf<T>().let { allNodes ->
                            allNodes.addAll(graph.values)
                            allNodes.forEach { node ->
                                val ra = alwaysRunAfter[toName(node)]
                                ra?.forEach { o ->
                                    dependencies[o]?.forEach {
                                        graph.addEdge(it, node)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        println("@@@ " + graph.dump())
        return graph
    }

    /**
     * Find the free tasks of the graph.
     */
    private fun <T> calculateFreeTasks(tasksByNames: Multimap<String, T>, reverseAfter: HashMap<String, String>)
            : Collection<T> {
        val freeTaskMap = hashMapOf<String, T>()
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
    private fun <T> calculateTransitiveClosure(projectName: String, tasksByNames: Multimap<String, T>, ti: TaskInfo):
            HashSet<T> {
        log(3, "Processing ${ti.taskName}")

        val transitiveClosure = hashSetOf<T>()
        val seen = hashSetOf(ti.taskName)
        val toProcess = hashSetOf(ti)
        var done = false
        while (! done) {
            val newToProcess = hashSetOf<TaskInfo>()
            log(3, "toProcess size: " + toProcess.size)
            toProcess.forEach { target ->

                val currentTask = TaskInfo(projectName, target.taskName)
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
                        newToProcess.add(TaskInfo(projectName, it))
                    }
                } else {
                    log(1, "Couldn't find task ${currentTask.taskName}: not applicable to project $projectName")
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

    // Both @Task and @IncrementalTask get stored as a TaskAnnotation so they can be treated uniformly.
    // They only differ in the way they are invoked (see below)
    private val taskAnnotations = arrayListOf<TaskAnnotation>()

    class TaskAnnotation(val method: Method, val plugin: IPlugin, val name: String, val description: String,
            val runBefore: Array<String>, val runAfter: Array<String>, val alwaysRunAfter: Array<String>,
            val callable: (Project) -> TaskResult)

    /**
     * Invoking a @Task means simply calling the method and returning its returned TaskResult.
     */
    fun toTaskAnnotation(method: Method, plugin: IPlugin, ta: Task)
            = TaskAnnotation(method, plugin, ta.name, ta.description, ta.runBefore, ta.runAfter, ta.alwaysRunAfter,
            { project ->
                method.invoke(plugin, project) as TaskResult
            })

    /**
     * Invoking an @IncrementalTask means invoking the method and then deciding what to do based on the content
     * of the returned IncrementalTaskInfo.
     */
    fun toTaskAnnotation(method: Method, plugin: IPlugin, ta: IncrementalTask)
            = TaskAnnotation(method, plugin, ta.name, ta.description, ta.runBefore, ta.runAfter, ta.alwaysRunAfter,
            incrementalManagerFactory.create().toIncrementalTaskClosure(ta.name, { project ->
                method.invoke(plugin, project) as IncrementalTaskInfo
            }))

    class PluginDynamicTask(val plugin: IPlugin, val task: DynamicTask)

    /** Tasks annotated with @Task or @IncrementalTask */
    val annotationTasks = arrayListOf<PluginTask>()

    /** Tasks provided by ITaskContributors */
    val dynamicTasks = arrayListOf<PluginDynamicTask>()

    fun addAnnotationTask(plugin: IPlugin, method: Method, annotation: Task) =
        taskAnnotations.add(toTaskAnnotation(method, plugin, annotation))

    fun addIncrementalTask(plugin: IPlugin, method: Method, annotation: IncrementalTask) =
        taskAnnotations.add(toTaskAnnotation(method, plugin, annotation))

    /**
     * Turn all the static and dynamic tasks into plug-in tasks, which are then suitable to be executed.
     */
    fun computePluginTasks(projects: List<Project>) {
        installAnnotationTasks(projects)
        installDynamicTasks(projects)
    }

    private fun installDynamicTasks(projects: List<Project>) {
        dynamicTasks.forEach { dynamicTask ->
            val task = dynamicTask.task
            projects.filter { dynamicTask.plugin.accept(it) }.forEach { project ->
                addTask(dynamicTask.plugin, project, task.name, task.description, task.runBefore, task.runAfter,
                        task.alwaysRunAfter, task.closure)
            }
        }
    }

    private fun installAnnotationTasks(projects: List<Project>) {
        taskAnnotations.forEach { staticTask ->
            val method = staticTask.method

            val methodName = method.declaringClass.toString() + "." + method.name
            log(3, "    Found task:${staticTask.name} method: $methodName")

            val plugin = staticTask.plugin
            projects.filter { plugin.accept(it) }.forEach { project ->
                addAnnotationTask(plugin, project, staticTask, staticTask.callable)
            }
        }
    }

    private fun addAnnotationTask(plugin: IPlugin, project: Project, annotation: TaskAnnotation,
            task: (Project) -> TaskResult) {
        addTask(plugin, project, annotation.name, annotation.description, annotation.runBefore.toList(),
                annotation.runAfter.toList(), annotation.alwaysRunAfter.toList(), task)
    }

    fun addTask(plugin: IPlugin, project: Project, name: String, description: String = "",
            runBefore: List<String> = listOf<String>(),
            runAfter: List<String> = listOf<String>(),
            alwaysRunAfter: List<String> = listOf<String>(),
            task: (Project) -> TaskResult) {
        annotationTasks.add(
                object : BasePluginTask(plugin, name, description, project) {
                    override fun call(): TaskResult2<PluginTask> {
                        val taskResult = task(project)
                        return TaskResult2(taskResult.success, taskResult.errorMessage, this)
                    }
                })
        runBefore.forEach { runBefore(it, name) }
        runAfter.forEach { runAfter(it, name) }
        alwaysRunAfter.forEach { alwaysRunAfter(it, name)}
    }

    //
    // Manage the tasks
    /////
}

class TaskWorker(val tasks: List<PluginTask>, val dryRun: Boolean, val messages: MutableList<String>)
        : IWorker2<PluginTask> {

    override fun call() : TaskResult2<PluginTask> {
        if (tasks.size > 0) {
            tasks[0].let {
                log(1, AsciiArt.taskColor(AsciiArt.horizontalSingleLine + " ${it.project.name}:${it.name}"))
            }
        }
        var success = true
        val errorMessages = arrayListOf<String>()
        tasks.forEach {
            val name = it.project.name + ":" + it.name
            val time = benchmarkMillis {
                val tr = if (dryRun) TaskResult() else it.call()
                success = success and tr.success
                if (tr.errorMessage != null) errorMessages.add(tr.errorMessage)
            }
            messages.add("$name: $time ms")
        }
        return TaskResult2(success, errorMessages.joinToString("\n"), tasks[0])
    }

//    override val timeOut : Long = 10000

    override val priority: Int = 0
}