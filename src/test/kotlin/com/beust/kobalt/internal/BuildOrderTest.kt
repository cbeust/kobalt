package com.beust.kobalt.internal

import com.beust.kobalt.TestModule
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.project
import org.testng.Assert
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
            Assert.assertEquals(taskInfos.map { it.id }, expectedTasks)
        }
    }
}
