package com.beust.kobalt.internal

import com.beust.kobalt.TestModule
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.project
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.BeforeClass
import org.testng.annotations.DataProvider
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject

@Guice(modules = arrayOf(TestModule::class))
class BuildOrderTest @Inject constructor(val taskManager: TaskManager) {
    @BeforeClass
    fun beforeClass() {
        Kobalt.init(TestModule())
    }

    @DataProvider
    fun tasks(): Array<Array<out Any>> {
        return arrayOf(
            arrayOf(listOf("p1:assemble"), listOf("p1:assemble")),
            arrayOf(listOf("p2:assemble"), listOf("p1:assemble", "p2:assemble")),
            arrayOf(listOf("p3:assemble"), listOf("p1:assemble", "p2:assemble", "p3:assemble"))
        )
    }

    @Test(dataProvider = "tasks")
    fun shouldBuildInRightOrder(tasks: List<String>, expectedTasks: List<String>) {
        val p1 = project { name = "p1" }
        val p2 = project(p1) { name = "p2" }
        val p3 = project(p2) { name = "p3" }

        val allProjects = listOf(p1, p2, p3)
        with(taskManager) {
            val taskInfos = calculateDependentTaskNames(tasks, allProjects)
            assertThat(taskInfos.map { it.id }).isEqualTo(expectedTasks)
        }
    }

    @DataProvider
    fun tasks2(): Array<Array<out Any>> {
        return arrayOf(
                arrayOf(listOf("p14:assemble"),
                        listOf(1, 2, 3, 4, 5, 8, 11, 6, 7, 9, 10, 12, 13, 14).map { "p$it:assemble"})
        )
    }

    @Test(dataProvider = "tasks2")
    fun shouldBuildInRightOrder2(tasks: List<String>, expectedTasks: List<String>) {
        val p1 = project { name ="p1" }
        val p2 = project { name ="p2" }
        val p3 = project { name ="p3" }
        val p4 = project { name ="p4" }
        val p5 = project { name ="p5" }
        val p6 = project(p5) { name ="p6" }
        val p7 = project(p5) { name ="p7" }
        val p8 = project { name ="p8" }
        val p9 = project(p6, p5, p2, p3) { name ="p9" }
        val p10 = project(p9) { name ="p10" }
        val p11 = project { name ="p11" }
        val p12 = project(p1, p7, p9, p10, p11) { name ="p12" }
        val p13 = project(p4, p8, p9, p12) { name ="p13" }
        val p14 = project(p12, p13) { name ="p14" }

        fun appearsBefore(taskInfos: List<TaskManager.TaskInfo>, first: String, second: String) {
            var sawFirst = false
            var sawSecond = false
            taskInfos.forEach { ti ->
                if (ti.project == first) {
                    sawFirst = true
                }
                if (ti.project == second) {
                    assertThat(sawFirst)
                            .`as`("Expected to see $first before $second in ${taskInfos.map {it.project}}")
                            .isTrue()
                    sawSecond = true
                }
            }
            assertThat(sawFirst).`as`("Didn't see $first").isTrue()
            assertThat(sawSecond).`as`("Didn't see $second").isTrue()
        }

        fun appearsBefore(taskInfos: List<TaskManager.TaskInfo>, firsts: List<String>, second: String) {
            firsts.forEach { first ->
                appearsBefore(taskInfos, first, second)
            }
        }

        // 1, 2, 3, 4, 5, 8, 11, 6, 7, 9, 10, 12, 13, 14
        val allProjects = listOf(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14)
        with(taskManager) {
            val taskInfos = calculateDependentTaskNames(tasks, allProjects)
            assertThat(taskInfos.size).isEqualTo(expectedTasks.size)
            appearsBefore(taskInfos, "p5", "p6")
            appearsBefore(taskInfos, "p5", "p7")
            appearsBefore(taskInfos, "p9", "p10")
            appearsBefore(taskInfos, listOf("p1", "p7", "p9", "p10", "p11"), "p12")
            appearsBefore(taskInfos, listOf("p4", "p8", "p9", "p12"), "p13")
            appearsBefore(taskInfos, listOf("p12", "p13"), "p14")
        }
    }

}
