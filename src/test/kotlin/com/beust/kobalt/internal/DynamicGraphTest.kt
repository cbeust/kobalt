package com.beust.kobalt.internal

import com.beust.kobalt.misc.Topological
import com.beust.kobalt.misc.kobaltLog
import org.assertj.core.api.Assertions.assertThat
import org.testng.Assert
import org.testng.annotations.Test
import java.util.*

class DynamicGraphTest {

    private fun <T> assertFreeNodesEquals(graph: DynamicGraph<T>, expected: Array<T>) {
        val h = HashSet(graph.freeNodes)
        val e = HashSet(expected.toList())
        Assert.assertEquals(h, e)
    }

    private fun <T> createFactory(runNodes: ArrayList<T>, errorFunction: (T) -> Boolean) : IThreadWorkerFactory<T> {
        return object: IThreadWorkerFactory<T> {
            override fun createWorkers(nodes: Collection<T>): List<IWorker<T>> {
                val result = arrayListOf<IWorker<T>>()
                nodes.forEach { result.add(Worker(runNodes, it, errorFunction)) }
                return result
            }
        }
    }

    class Worker<T>(val runNodes: ArrayList<T>, val n: T, val errorFunction: (T) -> Boolean) : IWorker<T> {
        override val priority = 0
        override val name: String get() = "[Worker " + runNodes.map { it.toString() }.joinToString(",") + "]"
        override fun call() : TaskResult2<T> {
            kobaltLog(2, "Running node $n")
            runNodes.add(n)
            return TaskResult2(errorFunction(n), value = n)
        }
    }

    @Test
    fun testExecutor() {
        DynamicGraph<String>().apply {
            addEdge("compile", "runApt")
            addEdge("compile", "generateVersion")

            val runNodes = arrayListOf<String>()
            val factory = createFactory(runNodes, { true })

            DynamicGraphExecutor(this, factory).run()
            Assert.assertEquals(runNodes.size, 3)
        }
    }

    @Test
    fun transitive() {
        DynamicGraph<Int>().apply {
            addEdge(1, 2)
            addEdge(1, 3)
            addEdge(2, 4)
            addEdge(6, 7)
            assertThat(transitiveClosure(1)).isEqualTo(listOf(1, 2, 3, 4))
            assertThat(transitiveClosure(2)).isEqualTo(listOf(2, 4))
            assertThat(transitiveClosure(3)).isEqualTo(listOf(3))
            assertThat(transitiveClosure(6)).isEqualTo(listOf(6, 7))
            assertThat(transitiveClosure(7)).isEqualTo(listOf(7))
            kobaltLog(1, "done")
        }
    }

    @Test
    private fun testExecutorWithSkip() {
        DynamicGraph<Int>().apply {
            // 2 and 3 depend on 1, 4 depend on 3, 10 depends on 4
            // 3 will blow up, which should make 4 and 10 skipped
            addEdge(2, 1)
            addEdge(3, 1)
            addEdge(4, 3)
            addEdge(10, 4)
            addEdge(5, 2)
            arrayListOf<Int>().let { runNodes ->
                val factory = createFactory(runNodes, { n -> n != 3 })
                val ex = DynamicGraphExecutor(this, factory)
                ex.run()
                Thread.`yield`()
                Assert.assertTrue(!runNodes.contains(4))
                Assert.assertTrue(!runNodes.contains(10))
            }
        }
    }

    @Test
    fun test8() {
        DynamicGraph<String>().apply {
            addEdge("b1", "a1")
            addEdge("b1", "a2")
            addEdge("b2", "a1")
            addEdge("b2", "a2")
            addEdge("c1", "b1")
            addEdge("c1", "b2")
            addNode("x")
            addNode("y")
            assertFreeNodesEquals(this, arrayOf("a1", "a2", "x", "y"))

            removeNode("a1")
            assertFreeNodesEquals(this, arrayOf("a2", "x", "y"))

            removeNode("a2")
            assertFreeNodesEquals(this, arrayOf("b1", "b2", "x", "y"))

            removeNode("b1")
            assertFreeNodesEquals(this, arrayOf("b2", "x", "y"))

            removeNode("b2")
            assertFreeNodesEquals(this, arrayOf("c1", "x", "y"))
        }
    }

    @Test
    fun test2() {
        DynamicGraph<String>().apply {
            addEdge("b1", "a1")
            addEdge("b1", "a2")
            addNode("x")
            assertFreeNodesEquals(this, arrayOf("a1", "a2", "x"))

            removeNode("a1")
            assertFreeNodesEquals(this, arrayOf("a2", "x"))

            removeNode("a2")
            assertFreeNodesEquals(this, arrayOf("b1", "x"))

            removeNode("b1")
            assertFreeNodesEquals(this, arrayOf("x"))
        }
    }

    @Test
    fun topologicalSort() {
        Topological<String>().apply {
            addEdge("b1", "a1")
            addEdge("b1", "a2")
            addEdge("b2", "a1")
            addEdge("b2", "a2")
            addEdge("c1", "b1")
            addEdge("c1", "b2")
            addNode("x")
            addNode("y")
            val sorted = sort()
            Assert.assertEquals(sorted, arrayListOf("a1", "a2", "x", "y", "b2", "b1", "c1"))
        }
    }

    @Test
    fun runAfter() {
        DynamicGraph<String>().apply {
            // a -> b
            // b -> c, d
            // e
            // Order should be: [c,d,e] [b] [a]
            addEdge("a", "b")
            addEdge("b", "c")
            addEdge("b", "d")
            addNode("e")
            kobaltLog(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("c", "d", "e"))

            removeNode("c")
            kobaltLog(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("d", "e"))

            removeNode("d")
            kobaltLog(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("b", "e"))

            removeNode("e")
            kobaltLog(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("b"))

            removeNode("b")
            kobaltLog(VERBOSE, dump())
            Assert.assertEquals(freeNodes, setOf("a"))

            removeNode("a")
            kobaltLog(VERBOSE, dump())
            Assert.assertTrue(freeNodes.isEmpty())
            Assert.assertTrue(nodes.isEmpty())
        }
    }

    @Test
    fun transitiveClosureGraphTest() {
        val graph = DynamicGraph<String>().apply {
            // a -> b
            // b -> c, d
            // e
            addEdge("a", "b")
            addEdge("b", "c")
            addEdge("b", "d")
            addNode("e")
        }
        val closure = DynamicGraph.transitiveClosureGraph("a", { s -> graph.childrenOf(s).toList() } )
        assertThat(closure.value).isEqualTo("a")
        val ca = closure.children
        assertThat(ca.map { it.value }).isEqualTo(listOf("b"))
        val cb = ca[0].children
        assertThat(cb.map { it.value }.toSet()).isEqualTo(setOf("d", "c"))

    }
}