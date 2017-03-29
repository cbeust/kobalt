package com.beust.kobalt.internal

import com.beust.kobalt.*
import com.beust.kobalt.misc.*
import com.google.common.collect.HashMultimap
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.*

open class TaskResult2<T>(success: Boolean, errorMessage: String?, val value: T) : TaskResult(success, errorMessage) {
    override fun toString() = com.beust.kobalt.misc.toString("TaskResult", "value", value, "success", success)
}

class DynamicGraph<T> {
    val VERBOSE = 3
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

        fun <T> transitiveClosureGraph(roots: List<T>, childrenFor: (T) -> List<T>,
                filter: (T) -> Boolean): List<Node<T>>
            = roots.map { transitiveClosureGraph(it, childrenFor, filter) }

        fun <T> transitiveClosureGraph(root: T, childrenFor: (T) -> List<T>,
                filter: (T) -> Boolean = { t: T -> true },
                seen: HashSet<T> = hashSetOf()) : Node<T> {
            val children = arrayListOf<Node<T>>()
            childrenFor(root).filter(filter).forEach { child ->
                if (! seen.contains(child)) {
                    seen.add(child)
                    val c = transitiveClosureGraph(child, childrenFor, filter, seen)
                    children.add(c)
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
        kobaltLog(VERBOSE, "  Removing node $t")
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
                kobaltLog(VERBOSE, "  Free nodes: $result")
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

    val name: String

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

class DynamicGraphExecutor<T>(val graph : DynamicGraph<T>, val factory: IThreadWorkerFactory<T>,
        val threadCount: Int = 1) {
    val executor : ExecutorService
            = Executors.newFixedThreadPool(threadCount, NamedThreadFactory("DynamicGraphExecutor"))
    val completion = ExecutorCompletionService<TaskResult2<T>>(executor)

    data class HistoryLog(val name: String, val timestamp: Long, val threadId: Long, val start: Boolean)

    val historyLog = arrayListOf<HistoryLog>()
    val threadIds = ConcurrentHashMap<Long, Long>()

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
            val callables : List<IWorker<T>> = factory.createWorkers(newFreeNodes).map {
                it -> object: IWorker<T> {
                    override val priority: Int
                        get() = it.priority

                    override val name: String get() = it.name
                    override fun call(): TaskResult2<T> {
                        val threadId = Thread.currentThread().id
                        historyLog.add(HistoryLog(it.name, System.currentTimeMillis(), threadId,
                                start = true))
                        threadIds.put(threadId, threadId)
                        val result = it.call()
                        historyLog.add(HistoryLog(it.name, System.currentTimeMillis(), Thread.currentThread().id,
                                start = false))
                        return result
                    }
                }
            }
            callables.forEach { completion.submit(it) }
            running += callables.size

            try {
                val future = completion.take()
                val taskResult = future.get(2, TimeUnit.SECONDS)
                running--
                if (taskResult.success) {
                    nodesRun.add(taskResult.value)
                    kobaltLog(3, "Task succeeded: $taskResult")
                    graph.removeNode(taskResult.value)
                    newFreeNodes.clear()
                    newFreeNodes.addAll(graph.freeNodes.minus(nodesRun))
                } else {
                    kobaltLog(3, "Task failed: $taskResult")
                    newFreeNodes.clear()
                    if (failedResult == null) {
                        failedResult = taskResult
                    }
                }
            } catch(ex: TimeoutException) {
                kobaltLog(3, "Time out")
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

    fun dumpHistory() {
        kobaltLog(1, "Thread report")

        val table = AsciiTable.Builder()
            .columnWidth(11)
        threadIds.keys.forEach {
            table.columnWidth(24)
        }
        table.header("Time (sec)")
        threadIds.keys.forEach {
            table.header("Thread " + it.toString())
        }

        fun toSeconds(millis: Long) = (millis / 1000).toInt().toString()

        fun displayCompressedLog(table: AsciiTable.Builder) : AsciiTable.Builder {
            data class CompressedLog(val timestamp: Long, val threadMap: HashMap<Long, String>)

            fun compressLog(historyLog: List<HistoryLog>): ArrayList<CompressedLog> {
                val compressed = arrayListOf<CompressedLog>()

                var currentLog: CompressedLog? = null

                val projectStart = hashMapOf<String, Long>()
                fun toName(hl: HistoryLog) : String {
                    var duration = ""
                    if (! hl.start) {
                        val start = projectStart[hl.name]
                        if (start != null) {
                            duration = " (" + ((hl.timestamp - start) / 1000)
                                    .toInt().toString() + ")"
                        } else {
                            kobaltLog(1, "DONOTCOMMIT")
                        }
                    }
                    return hl.name + duration
                }

                historyLog.forEach { hl ->
                    kobaltLog(1, "CURRENT LOG: " + currentLog + " HISTORY LINE: " + hl)
                    if (hl.start) {
                        projectStart[hl.name] = hl.timestamp
                    }
                    if (currentLog == null) {
                        currentLog = CompressedLog(hl.timestamp, hashMapOf(hl.threadId to hl.name))
                    } else currentLog?.let { cl ->
                        if (! hl.start || hl.timestamp - cl.timestamp < 1000) {
                            kobaltLog(1, "    CURRENT LOG IS WITHING ONE SECOND: $hl")
                            cl.threadMap[hl.threadId] = toName(hl)
                        } else {
                            kobaltLog(1, "  ADDING COMPRESSED LINE $cl")
                            compressed.add(cl)
                            currentLog = CompressedLog(hl.timestamp, hashMapOf(hl.threadId to toName(hl)))
                        }
                    }
                }
                return compressed
            }

            compressLog(historyLog).forEach {
                val row = arrayListOf<String>()
                row.add(toSeconds(it.timestamp))
                it.threadMap.values.forEach {
                    row.add(it)
                }
                table.addRow(row)
            }

            return table
        }

        fun displayRegularLog(table: AsciiTable.Builder) : AsciiTable.Builder {
            if (historyLog.any()) {
                if (historyLog[0] != null) {
                    val start = historyLog[0].timestamp
                    val projectStart = ConcurrentHashMap<String, Long>()
                    historyLog.forEach { line ->
                        val row = arrayListOf<String>()
                        row.add(toSeconds(line.timestamp - start))
                        threadIds.keys.forEach {
                            if (line.threadId == it) {
                                var duration = ""
                                if (line.start) {
                                    projectStart[line.name] = line.timestamp
                                } else {
                                    val projectStart = projectStart[line.name]
                                    if (projectStart != null) {
                                        duration = " (" + ((line.timestamp - projectStart) / 1000)
                                                .toInt().toString() + ")"
                                    } else {
                                        warn("Couldn't determine project start: " + line.name)
                                    }
                                }
                                row.add((line.name + duration))
                            } else {
                                row.add("")
                            }
                        }
                        table.addRow(row)
                    }
                } else {
                    warn("Couldn't find historyLog")
                }
            }
            return table
        }

        kobaltLog(1, displayRegularLog(table).build())
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
                        kobaltLog(1, "  Running worker $it")
                        return TaskResult2(true, null, it)
                    }

                    override val priority: Int get() = 0
                    override val name: String = "workerName"
                }
            }
        }
    }

    DynamicGraphExecutor(dg, factory).run()
}
