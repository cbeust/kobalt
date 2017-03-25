package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.BaseTest
import com.beust.kobalt.TestModule
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.BuildFileCompiler
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.DataProvider
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.util.*

@Guice(modules = arrayOf(TestModule::class))
class ProfileTest @Inject constructor(compilerFactory: BuildFileCompiler.IFactory) : BaseTest(compilerFactory) {

    private fun runTestWithProfile(enabled: Boolean, oldSyntax: Boolean) : Project {
        val projectVal = "p" + Math.abs(Random().nextInt())
        val projectDirectory = createTemporaryProjectDirectory()

        fun buildFileString(): String {
            return """
                import com.beust.kobalt.*
                import com.beust.kobalt.api.*
                val debug""" +
                    (if (oldSyntax) " = false\n" else " by profile()\n") +
            """
                val $projectVal = project {
                    name = if (debug) "profileOn" else "profileOff"
                    directory = "$projectDirectory"
                }
                """.trim()
        }

        val args = Args()
        if (enabled) args.profiles = "debug"
        val results = compileBuildFile(projectDirectory, buildFileString(), args)
        return results.projects[0]
    }

    @DataProvider
    fun dp() = arrayOf(
            arrayOf(false, "profileOff"),
            arrayOf(true, "profileOn"))

    @Test(dataProvider = "dp")
    fun profilesShouldWork(enabled: Boolean, expected: String) {
        Kobalt.init(TestModule())
        assertThat(runTestWithProfile(enabled, oldSyntax = false).name).isEqualTo(expected)
    }

    @Test(dataProvider = "dp")
    fun profilesShouldWorkOldSyntax(enabled: Boolean, expected: String) {
        Kobalt.init(TestModule())
        assertThat(runTestWithProfile(enabled, oldSyntax = true).name).isEqualTo(expected)
    }
}



