package com.beust.kobalt.internal

import com.beust.kobalt.misc.Topological
import com.beust.kobalt.misc.log
import org.testng.Assert
import org.testng.annotations.Test
import java.util.*

class DynamicGraphTest {

    private fun <T> assertFreeNodesEquals(graph: DG<T>, expected: Array<T>) {
        val h = HashSet(graph.freeNodes)
        val e = HashSet(expected.toList())
        Assert.assertEquals(h, e)
    }

    private fun <T> createFactory(runNodes: ArrayList<T>, errorFunction: (T) -> Boolean) : IThreadWorkerFactory<T> {
        return object: IThreadWorkerFactory<T> {
            override fun createWorkers(nodes: List<T>): List<IWorker<T>> {
                val result = arrayListOf<IWorker<T>>()
                nodes.forEach { result.add(Worker(runNodes, it, errorFunction)) }
                return result
            }
        }
    }

    class Worker<T>(val runNodes: ArrayList<T>, val n: T, val errorFunction: (T) -> Boolean) : IWorker<T> {
        override val priority = 0

        override fun call() : TaskResult2<T> {
            log(2, "Running node $n")
            runNodes.add(n)
            return TaskResult2(errorFunction(n), null, n)
        }
    }

    @Test
    fun testExecutor() {
        val dg = DynamicGraph<String>();
        dg.addEdge("compile", "runApt")
        dg.addEdge("compile", "generateVersion")

        val runNodes = arrayListOf<String>()
        val factory = createFactory(runNodes, { true })

        DynamicGraphExecutor(dg, factory).run()
        Assert.assertEquals(runNodes.size, 3)
    }


    @Test
    private fun testExecutorWithSkip() {

        val g = DynamicGraph<Int>()
        // 2 and 3 depend on 1, 4 depend on 3, 10 depends on 4
        // 3 will blow up, which should make 4 and 10 skipped
        g.addEdge(2, 1)
        g.addEdge(3, 1)
        g.addEdge(4, 3)
        g.addEdge(10, 4)
        g.addEdge(5, 2)
        val runNodes = arrayListOf<Int>()
        val factory = createFactory(runNodes, { n -> n != 3 })
        val ex = DynamicGraphExecutor(g, factory)
        ex.run()
        Thread.`yield`()
        Assert.assertTrue(! runNodes.contains(4))
        Assert.assertTrue(! runNodes.contains(10))
    }

    @Test
    public fun test8() {
        val dg = DG<String>()
        dg.addEdge("b1", "a1")
        dg.addEdge("b1", "a2")
        dg.addEdge("b2", "a1")
        dg.addEdge("b2", "a2")
        dg.addEdge("c1", "b1")
        dg.addEdge("c1", "b2")
        dg.addNode("x")
        dg.addNode("y")
        assertFreeNodesEquals(dg, arrayOf("a1", "a2", "y", "x"))

        dg.removeNode("a1")
        assertFreeNodesEquals(dg, arrayOf<String>())

        dg.removeNode("a2")
        assertFreeNodesEquals(dg, arrayOf("b1", "b2"))

        dg.removeNode("b1")
        assertFreeNodesEquals(dg, arrayOf<String>())

        dg.removeNode("b2")
        assertFreeNodesEquals(dg, arrayOf("c1"))
    }

    @Test
    public fun test2() {
        val dg = DG<String>()
        dg.addEdge("b1", "a1")
        dg.addEdge("b1", "a2")
        dg.addNode("x")
        assertFreeNodesEquals(dg, arrayOf("a1", "a2", "x" ))

        dg.removeNode("a1")
        assertFreeNodesEquals(dg, arrayOf("a2", "x"))

        dg.removeNode("a2")
        assertFreeNodesEquals(dg, arrayOf("b1", "x"))

        dg.removeNode("b1")
        assertFreeNodesEquals(dg, arrayOf("x"))
    }

    @Test
    fun topologicalSort() {
        val dg = Topological<String>()
        dg.addEdge("b1", "a1")
        dg.addEdge("b1", "a2")
        dg.addEdge("b2", "a1")
        dg.addEdge("b2", "a2")
        dg.addEdge("c1", "b1")
        dg.addEdge("c1", "b2")
        val sorted = dg.sort(arrayListOf("a1", "a2", "b1", "b2", "c1", "x", "y"))
        Assert.assertEquals(sorted, arrayListOf("a1", "a2", "x", "y", "b1", "b2", "c1"))
    }

    @Test
    fun runAfter() {

        DG<String>().apply {
            // a -> b
            // b -> c, d
            // e
            // Order should be: [c,d,e] [b] [a]
            addEdge("a", "b")
            addEdge("b", "c")
            addEdge("b", "d")
            addNode("e")
            log(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("c", "d", "e"))

            removeNode("c")
            log(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("d", "e"))

            removeNode("d")
            log(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("b", "e"))

            removeNode("e")
            log(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("b"))

            removeNode("b")
            log(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("a"))

            removeNode("a")
            log(VERBOSE, dump())
            Assert.assertTrue(freeNodes.isEmpty())
            Assert.assertTrue(nodes.isEmpty())
        }
    }

}