package com.beust.kobalt.maven

import com.beust.kobalt.Args
import com.beust.kobalt.BaseTest
import com.beust.kobalt.TestModule
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.maven.aether.Filters
import com.beust.kobalt.maven.aether.Scope
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.aether.util.filter.AndDependencyFilter
import org.testng.annotations.Guice
import org.testng.annotations.Test

@Guice(modules = arrayOf(TestModule::class))
class DependencyManagerTest @Inject constructor(val dependencyManager: DependencyManager,
        val dependencyManager2: DependencyManager2,
        val compilerFactory: BuildFileCompiler.IFactory) : BaseTest() {

    private fun assertContains(dependencies: List<IClasspathDependency>, vararg ids: String) {
        ids.forEach { id ->
            if (! dependencies.any { it.id.contains(id) }) {
                throw AssertionError("Couldn't find $id in $dependencies")
            }
        }
    }

    private fun assertDoesNotContain(dependencies: List<IClasspathDependency>, vararg ids: String) {
        ids.forEach { id ->
            if (dependencies.any { it.id.contains(id) }) {
                throw AssertionError("$id should not be found in $dependencies")
            }
        }
    }

    @Test(description = "Make sure that COMPILE scope dependencies get resolved properly")
    fun testScopeDependenciesShouldBeDownloaded() {
        val testDeps = listOf(dependencyManager.create("org.testng:testng:6.9.11"))

        val filter = AndDependencyFilter(Filters.EXCLUDE_OPTIONAL_FILTER, Filters.COMPILE_FILTER)

        // Should only resolve to TestNG
        dependencyManager.transitiveClosure(testDeps, filter).let { dependencies ->
            assertThat(dependencies.any { it.id.contains(":jcommander:") }).isFalse()
            assertContains(dependencies, ":testng:")
        }

        // Should resolve to TestNG and its dependencies
        dependencyManager.transitiveClosure(testDeps).let { dependencies ->
            assertContains(dependencies, ":jcommander:")
            assertContains(dependencies, ":bsh:")
            assertContains(dependencies, ":ant:")
            assertContains(dependencies, ":ant-launcher:")
            assertContains(dependencies, ":testng:")
        }
    }

    @Test
    fun honorRuntimeDependenciesBetweenProjects() {
        Kobalt.context = null
        val buildFileString = """
            import com.beust.kobalt.*

            val lib1 = project {
                name = "lib1"
                dependencies {
                    compile("com.beust:klaxon:0.26",
                        "com.beust:jcommander:1.48")
                }
            }

            val p = project(lib1) {
                name = "transitive1"
            }
        """

        val compileResult = compileBuildFile(sharedBuildFile, Args(), compilerFactory)
        val project2 = compileResult.projects[1]
        val dependencies = dependencyManager.calculateDependencies(project2, Kobalt.context!!, Filters.COMPILE_FILTER)
        assertContains(dependencies, ":klaxon:")
        assertContains(dependencies, ":guice:")
        assertDoesNotContain(dependencies, ":guave:")
    }

    val sharedBuildFile = """
            import com.beust.kobalt.*

            val lib2 = project {
                name = "lib2"
                dependencies {
                    // pick dependencies that don't have dependencies themselves, to avoid interferences
                    compile("com.beust:klaxon:0.27",
                        "com.google.inject:guice:4.0")
                    runtime("com.beust:jcommander:1.48")
                    compileOptional("junit:junit:4.12")
                }
            }

            val p = project(lib2) {
                name = "transitive2"
            }
        """

    @Test
    fun honorRuntimeDependenciesBetweenProjects2() {
        val buildFileString = """
            import com.beust.kobalt.*

            val lib2 = project {
                name = "lib2"
                dependencies {
                    // pick dependencies that don't have dependencies themselves, to avoid interferences
                    compile("com.beust:klaxon:0.27",
                        "com.google.inject:guice:4.0)
                    runtime("com.beust:jcommander:1.48")
                }
            }

            val p = project(lib2) {
                name = "transitive2"
            }
        """

        val compileResult = compileBuildFile(sharedBuildFile, Args(), compilerFactory)
        val project2 = compileResult.projects[1]

        Kobalt.context!!.let { context ->
            dependencyManager2.resolve(project2, context, isTest = false,
                    passedScopes = listOf(Scope.COMPILE)).let { dependencies ->
                assertContains(dependencies, ":klaxon:jar:0.27")
                assertContains(dependencies, ":guice:")
                assertDoesNotContain(dependencies, ":jcommander:")
                assertDoesNotContain(dependencies, ":junit:")
            }

            dependencyManager2.resolve(project2, context, isTest = false,
                    passedScopes = listOf(Scope.RUNTIME)).let { dependencies ->
                assertContains(dependencies, ":jcommander:")
                assertDoesNotContain(dependencies, ":klaxon:jar:0.27")
                assertDoesNotContain(dependencies, ":guice:")
                assertDoesNotContain(dependencies, ":junit:")
            }

            dependencyManager2.resolve(project2, context, isTest = false,
                    passedScopes = listOf(Scope.COMPILE, Scope.RUNTIME)).let { dependencies ->
                assertContains(dependencies, ":klaxon:")
                assertContains(dependencies, ":jcommander:")
                assertContains(dependencies, ":guice:")
                assertDoesNotContain(dependencies, ":junit:")
            }

        }

    }
}
