package com.beust.kobalt.internal

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.benchmarkMillis
import com.beust.kobalt.misc.kobaltError
import com.beust.kobalt.misc.log
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.TreeMultimap
import java.lang.reflect.Method
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskManager @Inject constructor(val args: Args,
        val incrementalManagerFactory: IncrementalManager.IFactory) {
    private val dependsOn = TreeMultimap.create<String, String>()
    private val reverseDependsOn = TreeMultimap.create<String, String>()
    private val runBefore = TreeMultimap.create<String, String>()
    private val runAfter = TreeMultimap.create<String, String>()
    private val alwaysRunAfter = TreeMultimap.create<String, String>()

    /**
     * Dependency: task2 depends on task 1.
     */
    fun dependsOn(task1: String, task2: String) = dependsOn.put(task2, task1)

    /**
     * Dependency: task2 depends on task 1.
     */
    fun reverseDependsOn(task1: String, task2: String) = reverseDependsOn.put(task2, task1)

    /**
     * Ordering: task1 runs before task 2.
     */
    fun runBefore(task1: String, task2: String) = runBefore.put(task2, task1)

    /**
     * Ordering: task2 runs after task 1.
     */
    fun runAfter(task1: String, task2: String) = runAfter.put(task2, task1)

    /**
     * Wrapper task: task2 runs after task 1.
     */
    fun alwaysRunAfter(task1: String, task2: String) = alwaysRunAfter.put(task2, task1)

    data class TaskInfo(val id: String) {
        constructor(project: String, task: String) : this(project + ":" + task)

        val project: String?
            get() = if (id.contains(":")) id.split(":")[0] else null
        val taskName: String
            get() = if (id.contains(":")) id.split(":")[1] else id

        fun matches(projectName: String) = project == null || project == projectName
    }

    class RunTargetResult(val exitCode: Int, val messages: List<String>)

    /**
     * @return the list of tasks available for the given project.
     *
     * There can be multiple tasks by the same name (e.g. PackagingPlugin and AndroidPlugin both
     * define "install"), so return a multimap.
     */
    fun tasksByNames(project: Project): ListMultimap<String, ITask> {
        return ArrayListMultimap.create<String, ITask>().apply {
            annotationTasks.filter {
                it.project.name == project.name
            }.forEach {
                put(it.name, it)
            }
            dynamicTasks.filter {
                it.plugin.accept(project)
            }.forEach {
                put(it.name, it)
            }
        }
    }

    fun runTargets(taskNames: List<String>, projects: List<Project>): RunTargetResult {
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
                val tasksByNames = tasksByNames(project)

                log(3, "Tasks:")
                tasksByNames.keys().forEach {
                    log(3, "  $it: " + tasksByNames.get(it))
                }

                val graph = createGraph2(project.name, taskNames, tasksByNames,
                        dependsOn, reverseDependsOn, runBefore, runAfter, alwaysRunAfter,
                        { task: ITask -> task.name },
                        { task: ITask -> task.plugin.accept(project) })

                //
                // Now that we have a full graph, run it
                //
                log(2, "About to run graph:\n  ${graph.dump()}  ")

                val factory = object : IThreadWorkerFactory<ITask> {
                    override fun createWorkers(nodes: Collection<ITask>)
                            = nodes.map { TaskWorker(listOf(it), args.dryRun, messages) }
                }

                val executor = DynamicGraphExecutor(graph, factory)
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

    val LOG_LEVEL = 3

    @VisibleForTesting
    fun <T> createGraph2(projectName: String, taskNames: List<String>, nodeMap: Multimap<String, T>,
            dependsOn: Multimap<String, String>,
            reverseDependsOn: Multimap<String, String>,
            runBefore: Multimap<String, String>,
            runAfter: Multimap<String, String>,
            alwaysRunAfter: Multimap<String, String>,
            toName: (T) -> String,
            accept: (T) -> Boolean):
            DynamicGraph<T> {

        val result = DynamicGraph<T>()
        val newToProcess = arrayListOf<T>()
        val seen = hashSetOf<String>()

        val always = ArrayListMultimap.create<String, String>()
        alwaysRunAfter.keySet().forEach { k ->
            alwaysRunAfter[k].forEach { v ->
                always.put(v, k)
            }
        }

        //
        // Turn the task names into the more useful TaskInfo and do some sanity checking on the way
        //
        val taskInfos = taskNames.map { TaskInfo(it) }.filter {
            if (!nodeMap.keys().contains(it.taskName)) {
                throw KobaltException("Unknown task: $it")
            }
                it.matches(projectName)
            }

        val toProcess = ArrayList(taskInfos)

        while (toProcess.size > 0) {

            fun addEdge(result: DynamicGraph<T>, from: String, to: String, newToProcess: ArrayList<T>, text: String) {
                val froms = nodeMap[from]
                froms.forEach { f: T ->
                    nodeMap[to].forEach { t: T ->
                        val tn = toName(t)
                        log(LOG_LEVEL, "                                  Adding edge ($text) $f -> $t")
                        result.addEdge(f, t)
                        newToProcess.add(t)
                    }
                }
            }

            /**
             * Whenever a task is added to the graph, we also add its alwaysRunAfter tasks.
             */
            fun processAlways(always: Multimap<String, String>, node: T) {
                log(LOG_LEVEL, "      Processing always for $node")
                always[toName(node)]?.let { to: Collection<String> ->
                    to.forEach { t ->
                        nodeMap[t].forEach { from ->
                            log(LOG_LEVEL, "                                  Adding always edge $from -> $node")
                            result.addEdge(from, node)
                        }
                    }
                    log(LOG_LEVEL, "        ... done processing always for $node")
                }
            }

            log(LOG_LEVEL, "  Current batch to process: $toProcess")
            val invertedReverseDependsOn = reverseMultimap(reverseDependsOn)

            toProcess.forEach { taskInfo ->
                val taskName = taskInfo.taskName
                log(LOG_LEVEL, "    ***** Current node: $taskName")
                nodeMap[taskName].forEach { processAlways(always, it) }

                //
                // dependsOn and reverseDependsOn are considered for all tasks, explicit and implicit
                //
                dependsOn[taskName].forEach { to ->
                    addEdge(result, taskName, to, newToProcess, "dependsOn")
                }
                reverseDependsOn[taskName].forEach { from ->
                    addEdge(result, from, taskName, newToProcess, "reverseDependsOn")
                }
                invertedReverseDependsOn[taskName].forEach { to ->
                    addEdge(result, taskName, to, newToProcess, "invertedReverseDependsOn")
                }

                //
                // runBefore and runAfter (task ordering) are only considered for explicit tasks (tasks that were
                // explicitly requested by the user)
                //
                runBefore[taskName].forEach { from ->
                    if (taskNames.contains(from)) {
                        addEdge(result, from, taskName, newToProcess, "runBefore")
                    }
                }
                runAfter[taskName].forEach { to ->
                    if (taskNames.contains(to)) {
                        addEdge(result, taskName, to, newToProcess, "runAfter")
                    }
                }
                seen.add(taskName)
            }

            newToProcess.forEach { processAlways(always, it) }

            toProcess.clear()
            toProcess.addAll(newToProcess.filter { ! seen.contains(toName(it))}.map { TaskInfo(toName(it)) })
            newToProcess.clear()
        }
        return result
    }

    private fun reverseMultimap(mm: Multimap<String, String>) : Multimap<String, String> {
        val result = TreeMultimap.create<String, String>()
        mm.keySet().forEach { key ->
            mm[key].forEach { value ->
                result.put(value, key)
            }
        }
        return result
    }

    /**
     * Create a dynamic graph representing all the tasks that need to be run.
     */
//    @VisibleForTesting
//    fun <T> createGraph(projectName: String, taskNames: List<String>, nodeMap: Multimap<String, out T>,
//            dependsOn: Multimap<String, String>,
//            reverseDependsOn: Multimap<String, String>,
//            runBefore: Multimap<String, String>,
//            runAfter: Multimap<String, String>,
//            toName: (T) -> String,
//            accept: (T) -> Boolean):
//            DynamicGraph<T> {
//
//        val result = DynamicGraph<T>()
//        taskNames.forEach { fullTaskName ->
//            val ti = TaskInfo(fullTaskName)
//            if (!nodeMap.keys().contains(ti.taskName)) {
//                throw KobaltException("Unknown task: $fullTaskName")
//            }
//
//            if (ti.matches(projectName)) {
//                val tiTaskName = ti.taskName
//                nodeMap[tiTaskName].forEach { task ->
//                    if (task != null && accept(task)) {
//                        val toProcess = arrayListOf(task)
//                        val newToProcess = hashSetOf<T>()
//
//                        fun maybeAddEdge(task: T, mm: Multimap<String, String>,
//                                seen: Set<String>,
//                                isDependency: Boolean,
//                                reverseEdges: Boolean = false): Boolean {
//                            var added = false
//                            val taskName = toName(task)
//                            mm[taskName]?.forEach { toName ->
//                                val addEdge = isDependency || (!isDependency && taskNames.contains(toName))
//                                if (addEdge) {
//                                    nodeMap[toName].forEach { to ->
//                                        if (reverseEdges) {
//                                            log(3, "     Adding reverse edge \"$to\" -> \"$task\" it=\"$toName\"")
//                                            added = true
//                                            result.addEdge(to, task)
//                                        } else {
//                                            log(3, "     Adding edge \"$task\" -> \"$to\"")
//                                            added = true
//                                            result.addEdge(task, to)
//                                        }
//                                        if (!seen.contains(toName(to))) {
//                                            log(3, "        New node to process: \"$to\"")
//                                            newToProcess.add(to)
//                                        } else {
//                                            log(3, "        Already seen: $to")
//                                        }
//                                    }
//                                }
//                            }
//                            return added
//                        }
//
//                        // These two maps indicate reversed dependencies so we want to have
//                        // a reverse map for them so we consider all the cases. For example,
//                        // if we are looking at task "a", we want to find all the relationships
//                        // that depend on "a" and also all the relationships that "a" depends on
//                        val invertedReverseDependsOn = reverseMultimap(reverseDependsOn)
//                        val invertedRunBefore = reverseMultimap(runBefore)
//                        val invertedDependsOn = reverseMultimap(dependsOn)
//                        val invertedRunAfter = reverseMultimap(runAfter)
//
//                        val seen = hashSetOf<String>()
//                        while (toProcess.size > 0) {
//                            log(3, " New batch of nodes to process: $toProcess")
//                            toProcess.filter { !seen.contains(toName(it)) }.forEach { current ->
//                                result.addNode(current)
//
//                                if (! maybeAddEdge(current, invertedDependsOn, seen, true, true)) {
//                                    maybeAddEdge(current, dependsOn, seen, true, false)
//                                }
//                                if (! maybeAddEdge(current, invertedRunAfter, seen, false, true)) {
//                                    maybeAddEdge(current, runAfter, seen, false, false)
//                                }
//                                if (! maybeAddEdge(current, reverseDependsOn, seen, true, true)) {
//                                    maybeAddEdge(current, invertedReverseDependsOn, seen, true, false)
//                                }
//                                if (! maybeAddEdge(current, runBefore, seen, false, true)) {
//                                    maybeAddEdge(current, invertedRunBefore, seen, false, false)
//                                }
//
//                                seen.add(toName(current))
//
//                                newToProcess.addAll(dependsOn[toName(current)].flatMap { nodeMap[it] })
//                            }
//                            toProcess.clear()
//                            toProcess.addAll(newToProcess)
//                            newToProcess.clear()
//                        }
//                    }
//                }
//            } else {
//                log(3, "Task $fullTaskName does not match the current project $projectName, skipping it")
//            }
//        }
//        return result
//    }

    /**
     * Find the free tasks of the graph.
     */
//    private fun <T> calculateFreeTasks(tasksByNames: Multimap<String, T>, runBefore: TreeMultimap<String, String>,
//            reverseAfter: HashMap<String,
//            String>)
//            : Collection<T> {
//        val freeTaskMap = hashMapOf<String, T>()
//        tasksByNames.keys().forEach {
//            if (! runBefore.containsKey(it) && ! reverseAfter.containsKey(it)) {
//                tasksByNames[it].forEach { t ->
//                    freeTaskMap.put(it, t)
//                }
//            }
//        }
//
//        return freeTaskMap.values
//    }

    /////
    // Manage the tasks
    //

    // Both @Task and @IncrementalTask get stored as a TaskAnnotation so they can be treated uniformly.
    // They only differ in the way they are invoked (see below)
    private val taskAnnotations = arrayListOf<TaskAnnotation>()

    class TaskAnnotation(val method: Method, val plugin: IPlugin, val name: String, val description: String,
            val dependsOn: Array<String>, val reverseDependsOn: Array<String>,
            val runBefore: Array<String>, val runAfter: Array<String>,
            val alwaysRunAfter: Array<String>,
            val callable: (Project) -> TaskResult) {
        override fun toString() = "[TaskAnnotation $name]"
    }

    /**
     * Invoking a @Task means simply calling the method and returning its returned TaskResult.
     */
    fun toTaskAnnotation(method: Method, plugin: IPlugin, ta: Task)
            = TaskAnnotation(method, plugin, ta.name, ta.description, ta.dependsOn, ta.reverseDependsOn,
                ta.runBefore, ta.runAfter, ta.alwaysRunAfter,
            { project ->
                method.invoke(plugin, project) as TaskResult
            })

    /**
     * Invoking an @IncrementalTask means invoking the method and then deciding what to do based on the content
     * of the returned IncrementalTaskInfo.
     */
    fun toTaskAnnotation(method: Method, plugin: IPlugin, ta: IncrementalTask)
            = TaskAnnotation(method, plugin, ta.name, ta.description, ta.dependsOn, ta.reverseDependsOn,
            ta.runBefore, ta.runAfter, ta.alwaysRunAfter,
            incrementalManagerFactory.create().toIncrementalTaskClosure(ta.name, { project ->
                method.invoke(plugin, project) as IncrementalTaskInfo
            }))

    /** Tasks annotated with @Task or @IncrementalTask */
    val annotationTasks = arrayListOf<PluginTask>()

    /** Tasks provided by ITaskContributors */
    val dynamicTasks = arrayListOf<DynamicTask>()

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
        dynamicTasks.forEach { task ->
            projects.filter { task.plugin.accept(it) }.forEach { project ->
                addTask(task.plugin, project, task.name, task.doc,
                        task.dependsOn, task.reverseDependsOn, task.runBefore, task.runAfter, task.alwaysRunAfter,
                        task.closure)
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
        addTask(plugin, project, annotation.name, annotation.description,
                annotation.dependsOn.toList(), annotation.reverseDependsOn.toList(),
                annotation.runBefore.toList(), annotation.runAfter.toList(),
                annotation.alwaysRunAfter.toList(), task)
    }

    fun addTask(plugin: IPlugin, project: Project, name: String, description: String = "",
            dependsOn: List<String> = listOf<String>(),
            reverseDependsOn: List<String> = listOf<String>(),
            runBefore: List<String> = listOf<String>(),
            runAfter: List<String> = listOf<String>(),
            alwaysRunAfter: List<String> = listOf<String>(),
            task: (Project) -> TaskResult) {
        annotationTasks.add(
                object : BasePluginTask(plugin, name, description, project) {
                    override fun call(): TaskResult2<ITask> {
                        val taskResult = task(project)
                        return TaskResult2(taskResult.success, taskResult.errorMessage, this)
                    }
                })
        dependsOn.forEach { dependsOn(it, name) }
        reverseDependsOn.forEach { reverseDependsOn(it, name) }
        runBefore.forEach { runBefore(it, name) }
        runAfter.forEach { runAfter(it, name) }
        alwaysRunAfter.forEach { alwaysRunAfter(it, name) }
    }

    /**
     * Invoked by the server whenever it's done processing a command so the state can be reset for the next command.
     */
    fun cleanUp() {
        annotationTasks.clear()
        dynamicTasks.clear()
        taskAnnotations.clear()
    }

    //
    // Manage the tasks
    /////
}

class TaskWorker(val tasks: List<ITask>, val dryRun: Boolean, val messages: MutableList<String>)
        : IWorker<ITask> {

    override fun call() : TaskResult2<ITask> {
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
