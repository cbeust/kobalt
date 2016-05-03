package com.beust.kobalt.internal

import com.beust.kobalt.TestModule
import com.beust.kobalt.misc.KobaltLogger
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
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
            var freeNodes = graph.freeNodes
            while (freeNodes.size > 0) {
                val toRemove = arrayListOf<T>()
                graph.freeNodes.forEach {
                    result.add(it)
                    toRemove.add(it)
                }
                toRemove.forEach {
                    graph.removeNode(it)
                }
                freeNodes = graph.freeNodes
            }
            return result
        }
    }

    private fun runCompileTasks(tasks: List<String>) : List<String> {
        val result = runTasks(tasks,
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                },
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("clean", "copyVersion")
                    put("compile", "postCompile")
                })
        return result
    }

    @Test(enabled = true)
    fun graphTest() {
//        KobaltLogger.LOG_LEVEL = 3
        Assert.assertEquals(runCompileTasks(listOf("compile")), listOf("compile", "assemble", "postCompile"))
        Assert.assertEquals(runCompileTasks(listOf("postCompile")), listOf("compile", "assemble", "postCompile"))
        Assert.assertEquals(runCompileTasks(listOf("compile", "postCompile")), listOf("compile", "assemble", "postCompile"))
        Assert.assertEquals(runCompileTasks(listOf("clean")), listOf("clean", "copyVersion"))
        Assert.assertEquals(runCompileTasks(listOf("clean", "compile")), listOf("clean", "compile", "assemble",
                "copyVersion", "postCompile"))
        Assert.assertEquals(runCompileTasks(listOf("assemble")), listOf("compile", "assemble", "postCompile"))
        Assert.assertEquals(runCompileTasks(listOf("clean", "assemble")), listOf("clean", "compile", "assemble",
                "copyVersion", "postCompile"))
    }

    val EMPTY_MULTI_MAP = ArrayListMultimap.create<String, String>()

    private fun runTasks(tasks: List<String>, dependsOn: Multimap<String, String> = EMPTY_MULTI_MAP,
            reverseDependsOn: Multimap<String, String> = EMPTY_MULTI_MAP,
            runBefore: Multimap<String, String> = EMPTY_MULTI_MAP,
            runAfter: Multimap<String, String> = EMPTY_MULTI_MAP): List<String> {

        val dependencies = TreeMultimap.create<String, String>().apply {
            listOf(dependsOn, reverseDependsOn, runBefore, runAfter).forEach { mm ->
                mm.keySet().forEach {
                    put(it, it)
                    mm[it].forEach {
                        put(it, it)
                    }
                }
            }
        }

        val graph = taskManager.createGraph("", tasks, dependencies,
                dependsOn, reverseDependsOn, runBefore, runAfter,
                { it }, { t -> true })
        val result = DryRunGraphExecutor(graph).run()
        return result
    }

    @Test
    fun exampleInTheDocTest() {
//        KobaltLogger.LOG_LEVEL = 3

        Assert.assertEquals(runTasks(listOf("assemble"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                },
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "copyVersionForWrapper")
                    put("copyVersionForWrapper", "assemble")
                }),
                listOf("compile", "copyVersionForWrapper", "assemble"))

        Assert.assertEquals(runTasks(listOf("compile"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }),
            listOf("clean", "compile", "example"))

        Assert.assertEquals(runTasks(listOf("compile"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                    put("compile", "example")
                }),
            listOf("clean", "example", "compile"))

        Assert.assertEquals(runTasks(listOf("compile"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                runAfter = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }),
            listOf("clean", "compile"))

        Assert.assertEquals(runTasks(listOf("compile"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                runBefore = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }),
            listOf("clean", "compile"))

        Assert.assertEquals(runTasks(listOf("compile", "example"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                runBefore = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }),
            listOf("clean", "compile", "example"))

        Assert.assertEquals(runTasks(listOf("compile", "example"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                runAfter = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }),
            listOf("clean", "example", "compile"))
    }

    @Test
    fun jacocoTest() {
//        KobaltLogger.LOG_LEVEL = 3
        val runTasks = runTasks(listOf("test"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("coverage", "test")
                    put("test", "compileTest")
                    put("test", "compile")
                    put("compileTest", "compile")
                },
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("enableJacoco", "test")
                    put("compileTest", "enableJacoco")
                })
        Assert.assertTrue(runTasks == listOf("compile", "compileTest", "enableJacoco", "test", "coverage"))
    }

    @Test
    fun simple() {
        val runTasks = runTasks(listOf("assemble"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                },
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("copyVersionForWrapper", "compile")
                })
        Assert.assertEquals(runTasks, listOf("copyVersionForWrapper", "compile", "assemble"))
    }
}

