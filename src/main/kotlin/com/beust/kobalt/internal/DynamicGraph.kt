package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.TaskResult
import com.beust.kobalt.misc.NamedThreadFactory
import com.beust.kobalt.misc.error
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.toString
import com.google.common.collect.HashMultimap
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.*

open class TaskResult2<T>(success: Boolean, val value: T) : TaskResult(success) {
    override fun toString() = toString("TaskResult", "value", value, "success", success)
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
        val factory: IThreadWorkerFactory<T>) {
    val executor = Executors.newFixedThreadPool(5, NamedThreadFactory("DynamicGraphExecutor"))
    val completion = ExecutorCompletionService<TaskResult2<T>>(executor)

    /**
     * @return 0 if all went well, > 0 otherwise
     */
    public fun run() : Int {
        var lastResult = TaskResult()
        var gotError = false
        var nodesRunning = 0
        while (graph.freeNodes.size > 0 && ! gotError) {
            log(3, "Current node count: ${graph.nodeCount}")
            synchronized(graph) {
                val freeNodes = graph.freeNodes
                freeNodes.forEach { graph.setStatus(it, DynamicGraph.Status.RUNNING)}
                log(3, "  ==> Submitting " + freeNodes)
                val callables : List<IWorker<T>> = factory.createWorkers(freeNodes)
                callables.forEach { completion.submit(it) }
                nodesRunning += callables.size

                // When a callable ends, see if it freed a node. If not, keep looping
                while (graph.nodesRunning.size > 0 && graph.freeNodes.size == 0 && ! gotError) {
                    try {
                        val future = completion.take()
                        val taskResult = future.get(2, TimeUnit.SECONDS)
                        lastResult = taskResult
                        log(3, "  <== Received task result $taskResult")
                        graph.setStatus(taskResult.value,
                            if (taskResult.success) {
                                DynamicGraph.Status.FINISHED
                            } else {
                                DynamicGraph.Status.ERROR
                            })
                    } catch(ex: TimeoutException) {
                        log(2, "Time out")
                    } catch(ex: Exception) {
                        if (ex.cause is InvocationTargetException) {
                            val ite = ex.cause
                            if (ite.targetException is KobaltException) {
                                throw (ex.cause as InvocationTargetException).targetException
                            } else {
                                error("Error: ${ite.cause?.message}", ite.cause)
                                gotError = true
                            }
                        } else {
                            error("Error: ${ex.message}", ex)
                            gotError = true
                        }
                    }
                }
            }
        }
        executor.shutdown()
        if (graph.freeNodes.size == 0 && graph.nodesReady.size > 0) {
            throw KobaltException("Couldn't find any free nodes but a few nodes still haven't run, there is " +
                    "a cycle in the dependencies.\n  Nodes left: " + graph.dump(graph.nodesReady))
        }
        return if (lastResult.success) 0 else 1
    }
}

/**
 * Representation of the graph of methods.
 */
public class DynamicGraph<T> {
    val nodesReady = linkedSetOf<T>()
    val nodesRunning = linkedSetOf<T>()
    private val nodesFinished = linkedSetOf<T>()
    private val nodesInError = linkedSetOf<T>()
    private val nodesSkipped = linkedSetOf<T>()
    private val dependedUpon = HashMultimap.create<T, T>()
    private val dependingOn = HashMultimap.create<T, T>()

    /**
     * Define a comparator for the nodes of this graph, which will be used
     * to order the free nodes when they are asked.
     */
//    public val comparator : Comparator<T>? = null

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
                } else if (getUnfinishedNodes(du).size == 0) {
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

            log(3, "    freeNodes: $result")
            return result
        }

    /**
     * @return a list of all the nodes that have a status other than FINISHED.
     */
    private fun getUnfinishedNodes(nodes: Set<T>) : Collection<T> {
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
                log(3, "Node skipped: $it")
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
                log(3, "Node in error: $node")
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
        get() = nodesReady.size + nodesRunning.size + nodesFinished.size

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

    fun dump(nodes: Collection<T>) : String {
        val result = StringBuffer()
        result.append("************ Graph dump ***************\n")
        val free = arrayListOf<T>()
        nodes.forEach { node ->
            val d = dependedUpon.get(node)
            if (d == null || d.isEmpty()) {
                free.add(node)
            }
        }

        result.append("Free nodes: $free").append("\n  Dependent nodes:\n")
        nodes.forEach {
            result.append("     $it -> ${dependedUpon.get(it)}\n")
        }
        return result.toString()
    }

    fun dump() = dump(nodesReady)
}

