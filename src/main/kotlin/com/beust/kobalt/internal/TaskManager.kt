package com.beust.kobalt.internal

import com.beust.kobalt.Plugins
import com.beust.kobalt.api.PluginTask
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.Task
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.maven.KobaltException
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashMultimap
import com.google.common.collect.TreeMultimap
import java.util.HashSet
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class TaskManager @Inject constructor(val plugins: Plugins) : KobaltLogger {
    private val dependentTaskMap = TreeMultimap.create<String, String>()

    /**
     * Called by plugins to indicate task dependencies defined at runtime. Keys depend on values.
     * Declare that `task1` depends on `task2`.
     */
    fun dependsOn(task1: String, task2: String) {
        dependentTaskMap.put(task1, task2)
    }

    public fun runTargets(targets: List<String>, projects: List<Project>) {
        val tasksByNames = HashMultimap.create<String, PluginTask>()

        projects.forEach { project ->
            log(1, "")
            log(1, "                   Building project ${project.name}")
            log(1, "")

            //
            // Locate all the tasks
            //
            plugins.allTasks.filter { it.project.name == project.name }.forEach { rt ->
                tasksByNames.put(rt.name, rt)
                if (rt.dependsOn.size() > 0) {
                    rt.dependsOn.forEach { d ->
                        dependentTaskMap.put(rt.name, d)
                    }
                }
            }

            val freeTaskMap = hashMapOf<String, PluginTask>()
            tasksByNames.keySet().forEach {
                if (!dependentTaskMap.containsKey(it)) freeTaskMap.put(it, tasksByNames.get(it).elementAt(0))
            }

            log(2, "Free tasks: ${freeTaskMap.keySet()}")
            log(2, "Dependent tasks:")
            dependentTaskMap.keySet().forEach { t ->
                log(2, "  ${t} -> ${dependentTaskMap.get(t)}}")
            }

            //
            // Find the tasks required to run the targets and add them to the dynamic graph
            //
            val transitiveClosure = hashSetOf<String>()
            val seen = HashSet(targets)
            val toProcess = HashSet(targets)
            var done = false
            while (!done) {
                val newToProcess = hashSetOf<String>()
                log(3, "toProcess size: " + toProcess.size())
                toProcess.forEach { target ->
                    log(3, "Processing ${target}")
                    val actualTarget =
                        if (target.contains(":")) {
                            // The target specifies a project explicitly
                            target.split(":").let {
                                val projectName = it[0]
                                if (projectName == project.name) {
                                    it[1]
                                } else {
                                    null
                                }
                            }
                        } else {
                            target
                        }
                    if (actualTarget != null) {
                        transitiveClosure.add(actualTarget)
                        val tasks = tasksByNames.get(actualTarget)
                        if (tasks.isEmpty()) {
                            throw KobaltException("Unknown task: ${target}")
                        }
                        tasks.forEach { task ->
                            val dependencyNames = dependentTaskMap.get(task.name)
                            dependencyNames.forEach { dependencyName ->
                                if (!seen.contains(dependencyName)) {
                                    newToProcess.add(dependencyName)
                                    seen.add(dependencyName)
                                }
                            }
                        }
                    } else {
                        log(2, "Target ${target} specified so not running it for project ${project.name}")
                    }
                }
                done = newToProcess.isEmpty()
                toProcess.clear()
                toProcess.addAll(newToProcess)
            }

            //
            // Create a dynamic graph with the transitive closure
            //
            val graph = DynamicGraph<PluginTask>()
            freeTaskMap.values().filter { transitiveClosure.contains(it.name) } forEach { graph.addNode(it) }
            dependentTaskMap.entries().filter {
                transitiveClosure.contains(it.key)
            }.forEach { entry ->
                plugins.findTasks(entry.key).filter { it.project.name == project.name }.forEach { from ->
                    plugins.findTasks(entry.value).filter { it.project.name == project.name }.forEach { to ->
                        if (from.project.name == to.project.name) {
                            graph.addEdge(from, to)
                        }
                    }
                }
            }

            //
            // Run the dynamic graph
            //
            val factory = object : IThreadWorkerFactory<PluginTask> {
                override public fun createWorkers(nodes: List<PluginTask>): List<IWorker<PluginTask>> {
                    val result = arrayListOf<IWorker<PluginTask>>()
                    nodes.forEach {
                        result.add(TaskWorker(arrayListOf(it)))
                    }
                    return result
                }
            }

            val executor = DynamicGraphExecutor(graph, factory)
            executor.run()
        }
    }
}

class TaskWorker(val tasks: List<PluginTask>) : IWorker<PluginTask>, KobaltLogger {
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
            val tr = it.call()
            success = success and tr.success
        }
        return TaskResult2(success, tasks.get(0))
    }

//    override val timeOut : Long = 10000

    override val priority: Int = 0
}