package com.beust.kobalt.internal

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.ProjectBuildStatus
import com.beust.kobalt.misc.kobaltLog
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import java.util.*

abstract class BaseProjectRunner {

    abstract fun runProjects(taskInfos: List<TaskManager.TaskInfo>, projects: List<Project>)
            : TaskManager.RunTargetResult

    companion object {
        val TAG = "graph"

        fun runBuildListenersForProject(project: Project, context: KobaltContext, start: Boolean,
                status: ProjectBuildStatus = ProjectBuildStatus.SUCCESS) {
            context.pluginInfo.buildListeners.forEach {
                if (start) it.projectStart(project, context) else it.projectEnd(project, context, status)
            }
        }

        fun runBuildListenersForTask(project: Project, context: KobaltContext, taskName: String, start: Boolean,
                success: Boolean = false) {
            context.pluginInfo.buildListeners.forEach {
                if (start) it.taskStart(project, context, taskName) else it.taskEnd(project, context, taskName, success)
            }
        }

        /**
         * Create a graph representing the tasks and their dependencies. That graph will then be run
         * in topological order.
         *
         * @taskNames is the list of tasks requested by the user. @nodeMap maps these tasks to the nodes
         * we'll be adding to the graph while @toName extracts the name of a node.
         */
        @VisibleForTesting
        fun <T> createTaskGraph(projectName: String, passedTasks: List<TaskManager.TaskInfo>,
                nodeMap: Multimap<String, T>,
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

            //
            // Reverse the always map so that tasks can be looked up.
            //
            val always = ArrayListMultimap.create<String, String>().apply {
                alwaysRunAfter.keySet().forEach { k ->
                    alwaysRunAfter[k].forEach { v ->
                        put(v, k)
                    }
                }
            }

            //
            // Keep only the tasks we need to run.
            //
            val taskInfos = passedTasks.filter {
                it.matches(projectName)
            }

            // The nodes we need to process, initialized with the set of tasks requested by the user.
            // As we run the graph and discover dependencies, new nodes get added to @param[newToProcess]. At
            // the end of the loop, @param[toProcess] is cleared and all the new nodes get added to it. Then we loop.
            val toProcess = ArrayList(taskInfos)

            while (toProcess.size > 0) {

                /**
                 * Add an edge from @param from to all its tasks.
                 */
                fun addEdge(result: DynamicGraph<T>, from: String, to: String, newToProcess: ArrayList<T>, text: String) {
                    val froms = nodeMap[from]
                    froms.forEach { f: T ->
                        nodeMap[to].forEach { t: T ->
                            kobaltLog(TAG, "                                  Adding edge ($text) $f -> $t")
                            result.addEdge(f, t)
                            newToProcess.add(t)
                        }
                    }
                }

                /**
                 * Whenever a task is added to the graph, we also add its alwaysRunAfter tasks.
                 */
                fun processAlways(always: Multimap<String, String>, node: T) {
                    kobaltLog(TAG, "      Processing always for $node")
                    always[toName(node)]?.let { to: Collection<String> ->
                        to.forEach { t ->
                            nodeMap[t].forEach { from ->
                                kobaltLog(TAG, "                                  Adding always edge $from -> $node")
                                result.addEdge(from, node)
                            }
                        }
                        kobaltLog(TAG, "        ... done processing always for $node")
                    }
                }

                kobaltLog(TAG, "  Current batch to process: $toProcess")

                //
                // Move dependsOn + reverseDependsOn in one multimap called allDepends
                //
                val allDependsOn = ArrayListMultimap.create<String, String>()
                dependsOn.keySet().forEach { key ->
                    dependsOn[key].forEach { value ->
                        allDependsOn.put(key, value)
                    }
                }
                reverseDependsOn.keySet().forEach { key ->
                    reverseDependsOn[key].forEach { value ->
                        allDependsOn.put(value, key)
                    }
                }

                //
                // Process each node one by one
                //
                toProcess.forEach { taskInfo ->
                    val taskName = taskInfo.taskName
                    kobaltLog(TAG, "    ***** Current node: $taskName")
                    nodeMap[taskName].forEach {
                        result.addNode(it)
                        processAlways(always, it)
                    }

                    //
                    // dependsOn and reverseDependsOn are considered for all tasks, explicit and implicit
                    //
                    allDependsOn[taskName].forEach { to ->
                        addEdge(result, taskName, to, newToProcess, "dependsOn")
                    }

                    //
                    // runBefore and runAfter (task ordering) are only considered for explicit tasks (tasks that were
                    // explicitly requested by the user)
                    //
                    passedTasks.map { it.taskName }.let { taskNames ->
                        runBefore[taskName].forEach { from ->
                            addEdge(result, from, taskName, newToProcess, "runBefore")
                        }
                        runAfter[taskName].forEach { to ->
                            addEdge(result, to, taskName, newToProcess, "runAfter")
                        }
                    }
                    seen.add(taskName)
                }

                newToProcess.forEach { processAlways(always, it) }

                toProcess.clear()
                toProcess.addAll(newToProcess.filter { !seen.contains(toName(it)) }.map { TaskManager.TaskInfo(toName(it)) })
                newToProcess.clear()
            }
            return result
        }
    }
}
