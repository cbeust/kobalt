package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
import com.beust.kobalt.TaskResult
import com.beust.kobalt.misc.NamedThreadFactory
import com.beust.kobalt.misc.error
import com.beust.kobalt.misc.log
import com.google.common.collect.HashMultimap
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.*

open class TaskResult2<T>(success: Boolean, errorMessage: String?, val value: T) : TaskResult(success, errorMessage) {
    override fun toString() = com.beust.kobalt.misc.toString("TaskResult", "value", value, "success", success)
}

class DynamicGraph<T> {
    val VERBOSE = 2
    val values : Collection<T> get() = nodes.map { it.value }
    val nodes = hashSetOf<PrivateNode<T>>()
    private val dependedUpon = HashMultimap.create<PrivateNode<T>, PrivateNode<T>>()
    private val dependingOn = HashMultimap.create<PrivateNode<T>, PrivateNode<T>>()

    class PrivateNode<T>(val value: T) {
        override fun hashCode() = value!!.hashCode()
        override fun equals(other: Any?) : Boolean {
            val result = if (other is PrivateNode<*>) other.value == value else false
            return result
        }
        override fun toString() = value.toString()
    }

    companion object {
        fun <T> transitiveClosure(root: T, childrenFor: (T) -> List<T>) : List<T> {
            val result = arrayListOf<T>()
            val seen = hashSetOf<T>()
            val toProcess = arrayListOf<T>().apply {
                add(root)
            }
            while (toProcess.any()) {
                val newToProcess = arrayListOf<T>()
                toProcess.forEach {
                    if (! seen.contains(it)) {
                        result.add(it)
                        newToProcess.addAll(childrenFor(it))
                        seen.add(it)
                    }
                }
                toProcess.clear()
                toProcess.addAll(newToProcess)
            }
            return result
        }

        class Node<T>(val value: T, val children: List<Node<T>>) {
            fun dump(root : Node<T> = this, indent: String = "") : String {
                return StringBuffer().apply {
                    append(indent).append(root.value).append("\n")
                    root.children.forEach {
                        append(dump(it, indent + "  "))
                    }
                }.toString()
            }
        }

        fun <T> transitiveClosureGraph(roots: List<T>, childrenFor: (T) -> List<T>) : List<Node<T>>
            = roots.map { transitiveClosureGraph(it, childrenFor) }

        fun <T> transitiveClosureGraph(root: T, childrenFor: (T) -> List<T>, seen: HashSet<T> = hashSetOf()) : Node<T> {
            val children = arrayListOf<Node<T>>()
            childrenFor(root).forEach { child ->
                if (! seen.contains(child)) {
                    val c = transitiveClosureGraph(child, childrenFor)
                    children.add(c)
                    seen.add(child)
                }
            }
            return Node(root, children)
        }
    }

    fun childrenOf(v: T) : Collection<T> = dependedUpon[PrivateNode(v)].map { it.value }

    fun transitiveClosure(root: T)
            = transitiveClosure(root) { element -> dependedUpon[PrivateNode(element)].map { it.value } }

    fun addNode(t: T) = synchronized(nodes) {
        nodes.add(PrivateNode(t))
    }

    fun removeNode(t: T) = synchronized(nodes) {
        log(VERBOSE, "  Removing node $t")
        PrivateNode(t).let { node ->
            nodes.remove(node)
            dependingOn.removeAll(node)
            val set = dependedUpon.keySet()
            val toReplace = arrayListOf<Pair<PrivateNode<T>, Collection<PrivateNode<T>>>>()
            set.forEach { du ->
                val l = ArrayList(dependedUpon[du])
                l.remove(node)
                toReplace.add(Pair(du, l))
            }
            toReplace.forEach {
                dependedUpon.replaceValues(it.first, it.second)
            }
        }
    }

    /**
     * Make "from" depend on "to" ("from" is no longer free).
     */
    fun addEdge(from: T, to: T) {
        val fromNode = PrivateNode(from)
        nodes.add(fromNode)
        val toNode = PrivateNode(to)
        nodes.add(PrivateNode(to))
        dependingOn.put(toNode, fromNode)
        dependedUpon.put(fromNode, toNode)
    }

    val freeNodes: Set<T>
        get() {
            val nonFree = hashSetOf<T>()
            synchronized(nodes) {
                nodes.forEach {
                    val du = dependedUpon[it]
                    if (du != null && du.size > 0) {
                        nonFree.add(it.value)
                    }
                }
                val result = nodes.map { it.value }.filter { !nonFree.contains(it) }.toHashSet()
                log(VERBOSE, "  Free nodes: $result")
                return result
            }
        }

    fun dump() : String {
        val result = StringBuffer()
        result.append("************ Graph dump ***************\n")
        val free = arrayListOf<PrivateNode<T>>()
        nodes.forEach { node ->
            val d = dependedUpon.get(node)
            if (d == null || d.isEmpty()) {
                free.add(node)
            }
        }

        result.append("All nodes: $values\n").append("Free nodes: $free").append("\nDependent nodes:\n")
        nodes.forEach {
            val deps = dependedUpon.get(it)
            if (! deps.isEmpty()) {
                result.append("     $it -> $deps\n")
            }
        }
        return result.toString()
    }
}

interface IWorker<T> : Callable<TaskResult2<T>> {
    /**
     * @return list of tasks this worker is working on.
     */
    //    val tasks : List<T>

    /**
     * @return the priority of this task.
     */
    val priority : Int
}

interface IThreadWorkerFactory<T> {

    /**
     * Creates {@code IWorker} for specified set of tasks. It is not necessary that
     * number of workers returned be same as number of tasks entered.
     *
     * @param nodes tasks that need to be executed
     * @return list of workers
     */
    fun createWorkers(nodes: Collection<T>) : List<IWorker<T>>
}

class DynamicGraphExecutor<T>(val graph : DynamicGraph<T>, val factory: IThreadWorkerFactory<T>) {
    val executor = Executors.newFixedThreadPool(1, NamedThreadFactory("DynamicGraphExecutor"))
    val completion = ExecutorCompletionService<TaskResult2<T>>(executor)

    fun run() : TaskResult {
        try {
            return run2()
        } finally {
            executor.shutdown()
        }
    }

    private fun run2() : TaskResult {
        var running = 0
        val nodesRun = hashSetOf<T>()
        var failedResult: TaskResult? = null
        val newFreeNodes = HashSet<T>(graph.freeNodes)
        while (failedResult == null && (running > 0 || newFreeNodes.size > 0)) {
            nodesRun.addAll(newFreeNodes)
            val callables : List<IWorker<T>> = factory.createWorkers(newFreeNodes)
            callables.forEach { completion.submit(it) }
            running += callables.size

            try {
                val future = completion.take()
                val taskResult = future.get(2, TimeUnit.SECONDS)
                running--
                if (taskResult.success) {
                    nodesRun.add(taskResult.value)
                    log(2, "Task succeeded: $taskResult")
                    graph.removeNode(taskResult.value)
                    newFreeNodes.clear()
                    newFreeNodes.addAll(graph.freeNodes.minus(nodesRun))
                } else {
                    log(2, "Task failed: $taskResult")
                    newFreeNodes.clear()
                    if (failedResult == null) {
                        failedResult = taskResult
                    }
                }
            } catch(ex: TimeoutException) {
                log(2, "Time out")
            } catch(ex: Exception) {
                val ite = ex.cause
                if (ite is InvocationTargetException) {
                    if (ite.targetException is KobaltException) {
                        throw (ex.cause as InvocationTargetException).targetException
                    } else {
                        error("Error: ${ite.cause?.message}", ite.cause)
                        failedResult = TaskResult(success = false, errorMessage = ite.cause?.message)
                    }
                } else {
                    error("Error: ${ex.message}", ex)
                    failedResult = TaskResult(success = false, errorMessage = ex.message)
                }
            }
        }
        return if (failedResult != null) failedResult else TaskResult()
    }
}

fun main(argv: Array<String>) {
    val dg = DynamicGraph<String>().apply {
        // a -> b
        // b -> c, d
        // e
        addEdge("a", "b")
        addEdge("b", "c")
        addEdge("b", "d")
        addNode("e")
    }
    val factory = object : IThreadWorkerFactory<String> {
        override fun createWorkers(nodes: Collection<String>): List<IWorker<String>> {
            return nodes.map {
                object: IWorker<String> {
                    override fun call(): TaskResult2<String>? {
                        log(1, "  Running worker $it")
                        return TaskResult2(true, null, it)
                    }

                    override val priority: Int get() = 0
                }
            }
        }
    }

    DynamicGraphExecutor(dg, factory).run()
}
