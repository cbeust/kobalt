package com.beust.kobalt.internal

import com.beust.kobalt.TestModule
import com.google.common.collect.TreeMultimap
import com.google.inject.Inject
import org.testng.Assert
import org.testng.annotations.Guice
import org.testng.annotations.Test

@Guice(modules = arrayOf(TestModule::class))
class TaskManagerTest @Inject constructor(val taskManager: TaskManager) {

    class DryRunGraphExecutor<T>(val graph: DynamicGraph<T>) {
        fun run() : List<T> {
            val result = arrayListOf<T>()
            while (graph.freeNodes.size > 0) {
                graph.freeNodes.forEach {
                    result.add(it)
                    graph.setStatus(it, DynamicGraph.Status.FINISHED)
                }
            }
            return result
        }
    }

    private fun runTasks(tasks: List<String>) : List<String> {
        val runBefore = TreeMultimap.create<String, String>().apply {
            put("assemble", "compile")
            put("compile", "clean")
        }
        val alwaysRunAfter = TreeMultimap.create<String, String>()
        val dependencies = TreeMultimap.create<String, String>().apply {
            listOf("assemble", "compile", "clean").forEach {
                put(it, it)
            }
        }
        val graph = taskManager.createGraph("", tasks, dependencies, runBefore, alwaysRunAfter,
                { it }, { t -> true })
        val result = DryRunGraphExecutor(graph).run()
        return result
    }

    @Test
    fun graphTest() {
        Assert.assertEquals(runTasks(listOf("assemble")), listOf("compile", "assemble"))
        Assert.assertEquals(runTasks(listOf("clean", "assemble")), listOf("clean", "compile", "assemble"))
    }
}

