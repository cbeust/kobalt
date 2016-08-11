package com.beust.kobalt.internal

import com.beust.kobalt.BaseTest
import com.beust.kobalt.TestModule
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.TreeMultimap
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Guice
import org.testng.annotations.Test

@Guice(modules = arrayOf(TestModule::class))
class TaskManagerTest : BaseTest() {

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

    //
//    @Test(enabled = false)
//    fun graphTest() {
////        KobaltLogger.LOG_LEVEL = 3
//        fun runCompileTasks(tasks: List<String>) : List<String> {
//            val result = runTasks(tasks,
//                    dependsOn = TreeMultimap.create<String, String>().apply {
//                        put("assemble", "compile")
//                    },
//                    reverseDependsOn = TreeMultimap.create<String, String>().apply {
//                        put("clean", "copyVersion")
//                    },
//                    alwaysRunAfter = TreeMultimap.create<String, String>().apply {
//                        put("postCompile", "compile")
//                    })
//            kobaltLog((1, "GRAPH RUN: " + result)
//            return result
//        }
//
//        Assert.assertEquals(runCompileTasks(listOf("compile")), listOf("compile", "assemble", "postCompile"))
//        Assert.assertEquals(runCompileTasks(listOf("postCompile")), listOf("compile", "assemble", "postCompile"))
//        Assert.assertEquals(runCompileTasks(listOf("compile", "postCompile")), listOf("compile", "assemble", "postCompile"))
//        Assert.assertEquals(runCompileTasks(listOf("clean")), listOf("clean", "copyVersion"))
//        Assert.assertEquals(runCompileTasks(listOf("clean", "compile")), listOf("clean", "compile", "assemble",
//                "copyVersion", "postCompile"))
//        Assert.assertEquals(runCompileTasks(listOf("assemble")), listOf("compile", "assemble", "postCompile"))
//        Assert.assertEquals(runCompileTasks(listOf("clean", "assemble")), listOf("clean", "compile", "assemble",
//                "copyVersion", "postCompile"))
//    }

    val EMPTY_MULTI_MAP = ArrayListMultimap.create<String, String>()

    private fun runTasks(tasks: List<String>, dependsOn: Multimap<String, String> = EMPTY_MULTI_MAP,
            reverseDependsOn: Multimap<String, String> = EMPTY_MULTI_MAP,
            runBefore: Multimap<String, String> = EMPTY_MULTI_MAP,
            runAfter: Multimap<String, String> = EMPTY_MULTI_MAP,
            alwaysRunAfter: Multimap<String, String> = EMPTY_MULTI_MAP): List<String> {

        val dependencies = TreeMultimap.create<String, String>().apply {
            listOf(dependsOn, reverseDependsOn, runBefore, runAfter, alwaysRunAfter).forEach { mm ->
                mm.keySet().forEach {
                    put(it, it)
                    mm[it].forEach {
                        put(it, it)
                    }
                }
            }
        }

        val graph = BaseProjectRunner.createTaskGraph("", tasks.map { TaskManager.TaskInfo(it) }, dependencies,
                dependsOn, reverseDependsOn, runBefore, runAfter, alwaysRunAfter,
                { it }, { t -> true })
        val result = DryRunGraphExecutor(graph).run()
//        kobaltLog((1, "GRAPH RUN: $result")
        return result
    }

    @Test(enabled = true)
    fun exampleInTheDocTest() {
//        KobaltLogger.LOG_LEVEL = 3

        runTasks(listOf("assemble"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                },
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "copyVersionForWrapper")
                    put("copyVersionForWrapper", "assemble")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("compile", "copyVersionForWrapper", "assemble"))
        }

//        runTasks(listOf("compile"),
//                dependsOn = TreeMultimap.create<String, String>().apply {
//                    put("compile", "clean")
//                },
//                reverseDependsOn = TreeMultimap.create<String, String>().apply {
//                    put("compile", "example")
//                }).let { runTask ->
//            Assert.assertEquals(runTask, listOf("clean", "compile", "example"))
//        }

        runTasks(listOf("compile"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                    put("compile", "example")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("clean", "example", "compile"))
        }

        runTasks(listOf("compile"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                runAfter = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("clean", "compile"))
        }

        runTasks(listOf("compile"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                runBefore = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("clean", "compile"))
        }

        runTasks(listOf("compile", "example"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                runBefore = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("clean", "compile", "example"))
        }

        runTasks(listOf("compile", "example"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("compile", "clean")
                },
                runAfter = TreeMultimap.create<String, String>().apply {
                    put("compile", "example")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("clean", "example", "compile"))
        }
    }

    @Test(enabled = true)
    fun jacocoTest() {
//        KobaltLogger.LOG_LEVEL = 3
        runTasks(listOf("test"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("test", "compileTest")
                    put("test", "compile")
                    put("compileTest", "compile")
                },
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("enableJacoco", "test")
                    put("compileTest", "enableJacoco")
                },
                alwaysRunAfter = TreeMultimap.create<String, String>().apply {
                    put("coverage", "test")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("compile", "compileTest", "enableJacoco", "test", "coverage"))
        }
    }

    @Test(enabled = true)
    fun simple() {
//        KobaltLogger.LOG_LEVEL = 3
        runTasks(listOf("assemble"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                },
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("copyVersionForWrapper", "assemble")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("compile", "copyVersionForWrapper", "assemble"))
        }
        runTasks(listOf("assemble"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                },
                alwaysRunAfter = TreeMultimap.create<String, String>().apply {
                    put("copyVersionForWrapper", "compile")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("compile", "assemble", "copyVersionForWrapper"))
        }
        runTasks(listOf("assemble"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                    put("compile", "copyVersionForWrapper")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("copyVersionForWrapper", "compile", "assemble"))
        }
        runTasks(listOf("assemble"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                    put("assemble", "copyVersionForWrapper")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("compile", "copyVersionForWrapper", "assemble"))
        }
        runTasks(listOf("assemble"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "compile")
                    put("compile", "copyVersionForWrapper")
                },
                alwaysRunAfter = TreeMultimap.create<String, String>().apply {
                    put("assemble", "copyVersionForWrapper")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("copyVersionForWrapper", "compile", "assemble"))
        }
    }

    @Test
    fun uploadGithub() {
        runTasks(listOf("uploadGithub"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("uploadGithub", "assemble")
                    put("uploadBintray", "assemble")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("assemble", "uploadGithub"))
        }
        runTasks(listOf("uploadGithub"),
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("assemble", "uploadGithub")
                    put("assemble", "uploadBintray")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("assemble", "uploadGithub"))
        }
    }

    @Test(enabled = true, description = "Make sure that dependsOn and reverseDependsOn have similar effects")
    fun symmetry() {
//        KobaltLogger.LOG_LEVEL = 3

        // Symmetry 1
        runTasks(listOf("task1"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("task2a", "task1")
                    put("task2b", "task1")
                }).let { runTasks ->

            assertThat(runTasks).isEqualTo(listOf("task1"))
        }
        runTasks(listOf("task1"),
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("task1", "task2a")
                    put("task1", "task2b")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("task1"))
        }

        // Symmetry 2
        runTasks(listOf("task2a"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("task2a", "task1")
                    put("task2b", "task1")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("task1", "task2a"))
        }
        runTasks(listOf("task2a"),
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("task1", "task2a")
                    put("task1", "task2b")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("task1", "task2a"))
        }

        // Symmetry 3
        runTasks(listOf("task1"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("task2", "task1")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("task1"))
        }
        runTasks(listOf("task1"),
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("task1", "task2")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("task1"))
        }

        // Symmetry 4
        runTasks(listOf("task2"),
                dependsOn = TreeMultimap.create<String, String>().apply {
                    put("task2", "task1")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("task1", "task2"))
        }
        runTasks(listOf("task2"),
                reverseDependsOn = TreeMultimap.create<String, String>().apply {
                    put("task1", "task2")
                }).let { runTasks ->
            assertThat(runTasks).isEqualTo(listOf("task1", "task2"))
        }
    }
}

