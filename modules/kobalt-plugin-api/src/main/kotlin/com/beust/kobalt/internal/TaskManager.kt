package com.beust.kobalt.internal

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.IncrementalTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.misc.Topological
import com.beust.kobalt.misc.kobaltLog
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.TreeMultimap
import java.lang.reflect.Method
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskManager @Inject constructor(val args: Args,
        val incrementalManagerFactory: IncrementalManager.IFactory,
        val kobaltLog: ParallelLogger) {
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
    fun runAfter(task1: String, task2: String) = runAfter.put(task1, task2)

    /**
     * Wrapper task: task2 runs after task 1.
     */
    fun alwaysRunAfter(task1: String, task2: String) = alwaysRunAfter.put(task2, task1)

    data class TaskInfo(val id: String) {
        constructor(project: String, task: String) : this(project + ":" + task)

        val project: String?
            get() = if (id.contains(':')) id.split(':')[0] else null
        val taskName: String
            get() = if (id.contains(':')) id.split(':')[1] else id

        fun matches(projectName: String) = project == null || project == projectName

        override fun toString() = id
    }

    class RunTargetResult(val taskResult: TaskResult, val timings: List<ProfilerInfo>)

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
        }
    }

//    @Inject
//    lateinit var pluginInfo: PluginInfo

    fun runTargets(passedTaskNames: List<String>, allProjects: List<Project>): RunTargetResult {
        // Check whether tasks passed at command line exist
        passedTaskNames.forEach {
            if (!hasTask(TaskInfo(it)))
                throw KobaltException("Unknown task: $it")
        }

        val pluginInfo = Kobalt.INJECTOR.getInstance(PluginInfo::class.java)
        var taskInfos = calculateDependentTaskNames(passedTaskNames, allProjects)

        // Remove non existing tasks (e.g. dynamic task defined for a single project)
        taskInfos = taskInfos.filter { hasTask(it) }

        val projectsToRun = findProjectsToRun(taskInfos, allProjects)
        val projectRunner =
            if (args.sequential) {
                SequentialProjectRunner({ p -> tasksByNames(p) }, dependsOn,
                        reverseDependsOn, runBefore, runAfter, alwaysRunAfter, args, pluginInfo)
            } else {
                ParallelProjectRunner({ p -> tasksByNames(p) }, dependsOn,
                        reverseDependsOn, runBefore, runAfter, alwaysRunAfter, args, pluginInfo, kobaltLog)
            }
        return projectRunner.runProjects(taskInfos, projectsToRun)
    }

    /**
     * Determine which projects to run based on the request tasks. Also make sure that all the requested projects
     * exist.
     */
    private fun findProjectsToRun(taskInfos: List<TaskInfo>, projects: List<Project>) : List<Project> {

        // Validate projects
        val result = LinkedHashSet<Project>()
        val projectMap = HashMap<String, Project>().apply {
            projects.forEach { put(it.name, it)}
        }

        // Extract all the projects we need to run from the tasks
        taskInfos.forEach {
            val p = it.project
            if (p != null && ! projectMap.containsKey(p)) {
                throw KobaltException("Unknown project: ${it.project}")
            }
            result.add(projectMap[it.project]!!)
        }

        // If at least one task didn't specify a project, run them all
        return if (result.any()) result.toList() else projects
    }

    class ProfilerInfo(val taskName: String, val durationMillis: Long)

    /**
     * If the user wants to run a single task on a single project (e.g. "kobalt:assemble"), we need to
     * see if that project depends on others and if it does, compile these projects first. This
     * function returns all these task names (including the dependent ones).
     */
    fun calculateDependentTaskNames(taskNames: List<String>, projects: List<Project>): List<TaskInfo> {
        return taskNames.flatMap { calculateDependentTaskNames(it, projects) }
    }

    private fun calculateDependentTaskNames(taskName: String, projects: List<Project>): List<TaskInfo> {
        fun sortProjectsTopologically(projects: List<Project>) : List<Project> {
            val topological = Topological<Project>().apply {
                projects.forEach { project ->
                    addNode(project)
                    project.allProjectDependedOn().forEach {
                        addEdge(project, it)
                    }
                }
            }
            val sortedProjects = topological.sort()
            return sortedProjects
        }

        val ti = TaskInfo(taskName)
        if (ti.project == null) {
            val result = sortProjectsTopologically(projects).map { TaskInfo(it.name, taskName) }
            return result
        } else {
            val rootProject = projects.find { it.name == ti.project }!!
            val allProjects = DynamicGraph.transitiveClosure(rootProject, Project::allProjectDependedOn)
            val sortedProjects = sortProjectsTopologically(allProjects)
            val sortedMaps = sortedProjects.map { TaskInfo(it.name, "compile")}
            val result = sortedMaps.subList(0, sortedMaps.size - 1) + listOf(ti)
            return result
        }
    }

    /////
    // Manage the tasks
    //

    // Both @Task and @IncrementalTask get stored as a TaskAnnotation so they can be treated uniformly.
    // They only differ in the way they are invoked (see below)
    private val taskAnnotations = arrayListOf<TaskAnnotation>()

    class TaskAnnotation(val method: Method, val plugin: IPlugin, val name: String, val description: String,
            val group: String,
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
            = TaskAnnotation(method, plugin, ta.name, ta.description, ta.group, ta.dependsOn, ta.reverseDependsOn,
                ta.runBefore, ta.runAfter, ta.alwaysRunAfter,
            { project ->
                Kobalt.context?.variant = Variant()
                method.invoke(plugin, project) as TaskResult
            })

    /**
     * Invoking an @IncrementalTask means invoking the method and then deciding what to do based on the content
     * of the returned IncrementalTaskInfo.
     */
    fun toTaskAnnotation(method: Method, plugin: IPlugin, ta: IncrementalTask)
            = TaskAnnotation(method, plugin, ta.name, ta.description, ta.group, ta.dependsOn, ta.reverseDependsOn,
            ta.runBefore, ta.runAfter, ta.alwaysRunAfter,
            incrementalManagerFactory.create().toIncrementalTaskClosure(ta.name, { project ->
                method.invoke(plugin, project) as IncrementalTaskInfo
            }, Variant()))

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
        installDynamicTasks()
    }

    private fun installDynamicTasks() {
        dynamicTasks.forEach { task ->
            addTask(task.plugin, task.project, task.name, task.doc, task.group,
                    task.dependsOn, task.reverseDependsOn, task.runBefore, task.runAfter, task.alwaysRunAfter,
                    task.closure)
        }
    }

    private fun installAnnotationTasks(projects: List<Project>) {
        taskAnnotations.forEach { staticTask ->
            val method = staticTask.method

            val methodName = method.declaringClass.toString() + "." + method.name
            kobaltLog(3, "    Found task:${staticTask.name} method: $methodName")

            val plugin = staticTask.plugin
            projects.filter { plugin.accept(it) }.forEach { project ->
                addAnnotationTask(plugin, project, staticTask, staticTask.callable)
            }
        }
    }

    private fun addAnnotationTask(plugin: IPlugin, project: Project, annotation: TaskAnnotation,
            task: (Project) -> TaskResult) {
        addTask(plugin, project, annotation.name, annotation.description, annotation.group,
                annotation.dependsOn.toList(), annotation.reverseDependsOn.toList(),
                annotation.runBefore.toList(), annotation.runAfter.toList(),
                annotation.alwaysRunAfter.toList(), task)
    }

    fun addTask(plugin: IPlugin, project: Project, name: String, description: String = "", group: String,
            dependsOn: List<String> = listOf<String>(),
            reverseDependsOn: List<String> = listOf<String>(),
            runBefore: List<String> = listOf<String>(),
            runAfter: List<String> = listOf<String>(),
            alwaysRunAfter: List<String> = listOf<String>(),
            task: (Project) -> TaskResult) {
        annotationTasks.add(
                object : BasePluginTask(plugin, name, description, group, project) {
                    override fun call(): TaskResult2<ITask> {
                        val taskResult = task(project)
                        return TaskResult2(taskResult.success, errorMessage = taskResult.errorMessage, value = this,
                            testResult = taskResult.testResult)
                    }
                })
        dependsOn.forEach { dependsOn(it, name) }
        reverseDependsOn.forEach { reverseDependsOn(it, name) }
        runBefore.forEach { runBefore(it, name) }
        runAfter.forEach { runAfter(it, name) }
        alwaysRunAfter.forEach { alwaysRunAfter(it, name) }
    }

    fun hasTask(ti: TaskInfo): Boolean {
        val taskName = ti.taskName
        val project = ti.project
        return annotationTasks.any { taskName == it.name && (project == null || project == it.project.name) }
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

class TaskWorker(val tasks: List<ITask>, val dryRun: Boolean, val pluginInfo: PluginInfo) : IWorker<ITask> {

    override fun call() : TaskResult2<ITask> {
        if (tasks.size > 0) {
            tasks[0].let {
                kobaltLog(1, AsciiArt.taskColor(AsciiArt.horizontalSingleLine + " ${it.project.name}:${it.name}"))
            }
        }
        var success = true
        val errorMessages = arrayListOf<String>()
        val context = Kobalt.context!!
        tasks.forEach {
            val name = it.project.name + ":" + it.name
            BaseProjectRunner.runBuildListenersForTask(it.project, context, name, start = true)
            val tr = if (dryRun) TaskResult() else it.call()
            BaseProjectRunner.runBuildListenersForTask(it.project, context, name, start = false, success = tr.success)
            success = success and tr.success
            tr.errorMessage?.let {
                errorMessages.add(it)
            }
        }
        return TaskResult2(success, errorMessage = errorMessages.joinToString("\n"), value = tasks[0])
    }

//    override val timeOut : Long = 10000

    override val priority: Int = 0
    override val name: String get() = "[Taskworker " + tasks.map(ITask::toString).joinToString(",") + "]"
}
