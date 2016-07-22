package com.beust.kobalt.maven

import com.beust.kobalt.TestModule
import com.beust.kobalt.api.IClasspathDependency
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Guice
import org.testng.annotations.Test

@Guice(modules = arrayOf(TestModule::class))
class DependencyManagerTest @Inject constructor(val dependencyManager: DependencyManager) {

    @Test(description = "Make sure that COMPILE scope dependencies get resolved properly")
    fun testScopeDependenciesShouldBeDownloaded() {
        val testDeps = listOf(dependencyManager.create("org.testng:testng:6.9.11"))

        fun assertContains(dependencies: List<IClasspathDependency>, vararg ids: String) {
            ids.forEach { id ->
                assertThat(dependencies.any { it.id.contains(id) }).isTrue()
            }
        }

        // Should only resolve to TestNG
        dependencyManager.transitiveClosure(testDeps, isTest = false).let { dependencies ->
            assertThat(dependencies.any { it.id.contains(":jcommander:") }).isFalse()
            assertContains(dependencies, ":testng:")
        }

        // Should resolve to TestNG and its dependencies
        dependencyManager.transitiveClosure(testDeps, isTest = true).let { dependencies ->
            assertContains(dependencies, ":jcommander:")
            assertContains(dependencies, ":bsh:")
            assertContains(dependencies, ":ant:")
        }

    }

}
