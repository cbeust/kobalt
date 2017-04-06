package com.beust.kobalt.internal

import com.beust.kobalt.BaseTest
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.Scope
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class ExcludeTest @Inject constructor(compilerFactory: BuildFileCompiler.IFactory,
        val dependencyManager: DependencyManager) : BaseTest(compilerFactory) {

    val EXCLUDED_DEPENDENCY = "org.codehaus.plexus:plexus-utils:jar:3.0.22"

    @DataProvider
    fun dp() = arrayOf<Array<String?>>(
            arrayOf(null),
            arrayOf(EXCLUDED_DEPENDENCY)
    )

    @Test(dataProvider = "dp", description = "Text exclusions that apply to the whole project")
    fun globalExcludeShouldWork(excludedDependency: String?) {
        val projectText = """
                dependencies {
                    compile("org.apache.maven:maven-model:jar:3.3.9")
            """ +
                (if (excludedDependency != null) """exclude("$excludedDependency")""" else "") +
            """
                }
            """

        val project = compileSingleProject(projectText)
        val allIds = dependencyManager.calculateDependencies(project, Kobalt.context!!,
                scopes = listOf(Scope.COMPILE))
            .map { it.id }
        if (excludedDependency != null) {
            assertThat(allIds).doesNotContain(excludedDependency)
        } else {
            assertThat(allIds).contains(EXCLUDED_DEPENDENCY)
        }
    }

    @DataProvider
    fun dp2() = arrayOf<Array<Any?>>(
        arrayOf(null, 8, ""),
        arrayOf("{ exclude(\".*org.apache.*\") }", 4, "org.apache"),
        arrayOf("{ exclude(groupId    = \"org.apache.*\") }", 4, "org.apache"),
        arrayOf("{ exclude(artifactId = \".*core.*\") }", 7, "core"),
        arrayOf("{ exclude(artifactId = \"httpcore\", version = \"4.3.3\") }", 7, "httpcore"),
        arrayOf("{ exclude(version = \"4.3.3\") }", 7, "httpcore"),
        arrayOf("{ exclude(artifactId = \"commons.codec\") }", 7, "commons-codec")
    )

    @Test(dataProvider = "dp2", description = "Text exclusions tied to a specific dependency")
    fun localExcludeShouldWork(excludedDependency: String?, expectedCount: Int, excludedString: String) {
        val projectText = """
                dependencies {
                    compile("org.eclipse.jgit:org.eclipse.jgit:4.5.0.201609210915-r")
            """ +
                (if (excludedDependency != null) """$excludedDependency""" else "") +
                """
                }
            """

        val project = compileSingleProject(projectText)
        val allIds = dependencyManager.calculateDependencies(project, Kobalt.context!!,
                scopes = listOf(Scope.COMPILE))
                .map { it.id }

        assertThat(allIds.size).isEqualTo(expectedCount)
        if (excludedDependency != null) {
            if (allIds.any { it.contains(excludedString) }) {
                throw AssertionError("id's should not contain any string \"$excludedString\": $allIds")
            }
        } else {
            assertThat(allIds.filter { it.contains("org.apache") }.size).isEqualTo(2)
        }
    }
}


