package com.beust.kobalt.internal

import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.NamedThreadFactory
import com.beust.kobalt.misc.ToString
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashMultimap
import com.google.common.collect.TreeMultimap
import java.util.*
import java.util.concurrent.*

open class TaskResult2<T>(success: Boolean, val value: T) : TaskResult(success) {
    override fun toString() = ToString("TaskResult", "success", success, "value", value).s
}

public interface IWorker<T> : Callable<TaskResult2<T>> {
    /**
     * @return list of tasks this worker is working on.
     */
    //    val tasks : List<T>

    /**
     * @return the priority of this task.
     */
    val priority : Int
}

public interface IThreadWorkerFactory<T> {

    /**
     * Creates {@code IWorker} for specified set of tasks. It is not necessary that
     * number of workers returned be same as number of tasks entered.
     *
     * @param nodes tasks that need to be executed
     * @return list of workers
     */
    fun createWorkers(nodes: List<T>) : List<IWorker<T>>
}

public class DynamicGraphExecutor<T>(val graph: DynamicGraph<T>,
        val factory: IThreadWorkerFactory<T>) : KobaltLogger {
    val executor = Executors.newFixedThreadPool(5, NamedThreadFactory("DynamicGraphExecutor"))
    val completion = ExecutorCompletionService<TaskResult2<T>>(executor)

    public fun run() {
        while (graph.freeNodes.size() > 0) {
            log(2, "Current count: ${graph.nodeCount}")
            synchronized(graph) {
                val freeNodes = graph.freeNodes
                freeNodes.forEach { graph.setStatus(it, DynamicGraph.Status.RUNNING)}
                log(2, "submitting free nodes ${freeNodes}")
                val callables : List<IWorker<T>> = factory.createWorkers(freeNodes)
                callables.forEach { completion.submit(it) }
                var n = callables.size()

                // When a callable ends, see if it freed a node. If not, keep looping
                while (n > 0 && graph.freeNodes.size() == 0) {
                    try {
                        val future = completion.take()
                        val taskResult = future.get(2, TimeUnit.SECONDS)
                        log(2, "Received task result ${taskResult}")
                        n--
                        graph.setStatus(taskResult.value,
                            if (taskResult.success) {
                                DynamicGraph.Status.FINISHED
                            } else {
                                DynamicGraph.Status.ERROR
                            })
                    } catch(ex: TimeoutException) {
                        log(2, "Time out")
                    }
                }
            }
        }
        executor.shutdown()
    }
}

/**
 * Representation of the graph of methods.
 */
public class DynamicGraph<T> : KobaltLogger {
    private val DEBUG = false

    private val nodesReady = linkedSetOf<T>()
    private val nodesRunning = linkedSetOf<T>()
    private val nodesFinished = linkedSetOf<T>()
    private val nodesInError = linkedSetOf<T>()
    private val nodesSkipped = linkedSetOf<T>()
    private val dependedUpon = ArrayListMultimap.create<T, T>()
    private val dependingOn = ArrayListMultimap.create<T, T>()

    /**
     * Define a comparator for the nodes of this graph, which will be used
     * to order the free nodes when they are asked.
     */
    public val comparator : Comparator<T>? = null

    enum class Status {
        READY, RUNNING, FINISHED, ERROR, SKIPPED
    }

    /**
     * Add a node to the graph.
     */
    public fun addNode(value: T) : T {
        nodes.add(value)
        nodesReady.add(value)
        return value
    }

    /**
     * Add an edge between two nodes, which don't have to already be in the graph
     * (they will be added by this method). Makes "to" depend on "from".
     */
    public fun addEdge(from: T, to: T) {
        nodes.add(from)
        nodes.add(to)
        val fromNode = addNode(from)
        val toNode = addNode(to)
        dependingOn.put(toNode, fromNode)
        dependedUpon.put(fromNode, toNode)
    }

    /**
     * @return a set of all the nodes that don't depend on any other nodes.
     */
    public val freeNodes : List<T>
        get() {
            val result = arrayListOf<T>()
            nodesReady.forEach { m ->
                // A node is free if...

                val du = dependedUpon.get(m)
                // - no other nodes depend on it
                if (! dependedUpon.containsKey(m)) {
                    result.add(m)
                } else if (getUnfinishedNodes(du).size() == 0) {
                    result.add(m)
                }
            }

            // Sort the free nodes if requested (e.g. priorities)
//            if (! result.isEmpty()) {
//                if (comparator != null) {
//                    Collections.sort(result, comparator)
//                    debug("Nodes after sorting:" + result.get(0))
//                }
//            }

            log(2, "freeNodes: ${result}")
            return result
        }

    /**
     * @return a list of all the nodes that have a status other than FINISHED.
     */
    private fun getUnfinishedNodes(nodes: List<T>) : Collection<T> {
        val result = hashSetOf<T>()
        nodes.forEach { node ->
            if (nodesReady.contains(node) || nodesRunning.contains(node)) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Set the status for a set of nodes.
     */
    public fun setStatus(nodes: Collection<T>, status: Status) {
        nodes.forEach { setStatus(it, status) }
    }

    /**
     * Mark all dependees of this node SKIPPED
     */
    private fun setSkipStatus(node: T, status: Status) {
        dependingOn.get(node).forEach {
            if (! nodesSkipped.contains(it)) {
                log(2, "Node skipped: ${it}")
                nodesSkipped.add(it)
                nodesReady.remove(it)
                setSkipStatus(it, status)
            }
        }
    }

    /**
     * Set the status for a node.
     */
    public fun setStatus(node: T, status: Status) {
        removeNode(node);
        when(status) {
            Status.READY -> nodesReady.add(node)
            Status.RUNNING -> nodesRunning.add(node)
            Status.FINISHED -> nodesFinished.add(node)
            Status.ERROR -> {
                log(2, "Node in error: ${node}")
                nodesReady.remove(node)
                nodesInError.add(node)
                setSkipStatus(node, status)
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    private fun removeNode(node: T) {
        if (! nodesReady.remove(node)) {
            if (! nodesRunning.remove(node)) {
                nodesFinished.remove(node)
            }
        }
    }

    /**
     * @return the number of nodes in this graph.
     */
    public val nodeCount: Int
        get() = nodesReady.size() + nodesRunning.size() + nodesFinished.size()

    override public fun toString() : String {
        val result = StringBuilder("[DynamicGraph ")
        result.append("\n  Ready:" + nodesReady)
        result.append("\n  Running:" + nodesRunning)
        result.append("\n  Finished:" + nodesFinished)
        result.append("\n  Edges:\n")
        //        dependingOn.entrySet().forEach { es ->
        //            result.append("     " + es.getKey() + "\n");
        //            es.getValue().forEach { t ->
        //                result.append("        " + t + "\n");
        //            }
        //        }
        result.append("]");
        return result.toString();
    }

    val nodes = hashSetOf<T>()

    fun dump() : String {
        val result = StringBuffer()
        val free = arrayListOf<T>()
        nodesReady.forEach { node ->
            val d = dependedUpon.get(node)
            if (d == null || d.isEmpty()) {
                free.add(node)
            }
        }

        result.append("Free: $free").append("\n  Dependencies:\n")
        dependedUpon.keySet().forEach {
            result.append("     $it -> ${dependedUpon.get(it)}\n")
        }
        return result.toString()
    }
}

