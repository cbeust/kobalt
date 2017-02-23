package com.beust.kobalt.internal

import com.beust.kobalt.BaseTest
import com.beust.kobalt.TestModule
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.Scope
import com.beust.kobalt.misc.KobaltLogger
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.DataProvider
import org.testng.annotations.Guice
import org.testng.annotations.Test

@Guice(modules = arrayOf(TestModule::class))
class ExcludeTest @Inject constructor(compilerFactory: BuildFileCompiler.IFactory,
        val dependencyManager: DependencyManager) : BaseTest(compilerFactory) {

    val EXCLUDED_DEPENDENCY = "org.codehaus.plexus:plexus-utils:jar:3.0.22"

    @DataProvider
    fun dp() = arrayOf<Array<String?>>(
            arrayOf("p1", null),
            arrayOf("p2", EXCLUDED_DEPENDENCY)
    )

   @Test(dataProvider = "dp")
    fun excludeShouldWork(projectName: String, excludedDependency: String?) {
        val buildFileString = """
            import com.beust.kobalt.*
            import com.beust.kobalt.api.*
            val $projectName = project {
                name = "$projectName"
                dependencies {
                    compile("org.apache.maven:maven-model:jar:3.3.9")
        """ +
            (if (excludedDependency != null) """exclude("$excludedDependency")""" else "") +
        """
                }
            }
            """

        KobaltLogger.LOG_LEVEL = 3
        val compileResult = compileBuildFile(buildFileString)

        val project = compileResult.projects.first { it.name == projectName }
        val allIds = dependencyManager.calculateDependencies(project, Kobalt.context!!,
                scopes = listOf(Scope.COMPILE))
            .map { it.id }
        if (excludedDependency != null) {
            assertThat(allIds).doesNotContain(excludedDependency)
        } else {
            assertThat(allIds).contains(EXCLUDED_DEPENDENCY)
        }

    }
}


