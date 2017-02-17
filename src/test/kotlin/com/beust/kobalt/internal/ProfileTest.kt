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

@Guice(modules = arrayOf(TestModule::class))
class ProfileTest @Inject constructor(val compilerFactory: BuildFileCompiler.IFactory) : BaseTest() {

    private fun runTestWithProfile(enabled: Boolean) : Project {
        val buildFileString = """
            import com.beust.kobalt.*
            import com.beust.kobalt.api.*
            val profile = false
            val p = project {
                name = if (profile) "profileOn" else "profileOff"
            }
            """

        val args = Args()
        if (enabled) args.profiles = "profile"
        val compileResult = compileBuildFile(buildFileString, args, compilerFactory)
        return compileResult.projects[0]
    }

    @DataProvider
    fun dp() = arrayOf(
            arrayOf(false, "profileOff"),
            arrayOf(true, "profileOn"))

    @Test(dataProvider = "dp")
    fun profilesShouldWork(enabled: Boolean, expected: String) {
        Kobalt.init(TestModule())
        assertThat(runTestWithProfile(enabled).name).isEqualTo(expected)
    }
}


