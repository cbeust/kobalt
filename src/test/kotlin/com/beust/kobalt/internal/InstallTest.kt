package com.beust.kobalt.internal

import com.beust.kobalt.*
import com.beust.kobalt.app.BuildFileCompiler
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.testng.IModuleFactory
import org.testng.ITestContext
import org.testng.annotations.Test
import java.io.File

class ModuleFactory : IModuleFactory {
    companion object {
        val TEST_MODULE = TestModule()
    }

    override fun createModule(tc: ITestContext?, c: Class<*>?) = TEST_MODULE
}

class InstallTest @Inject constructor(compilerFactory: BuildFileCompiler.IFactory) : BaseTest(compilerFactory) {

    private fun toPath(s: String) = "\"\${project.directory}/$s\""
    private fun from(path: String) = "from(" + toPath(path) + ")"
    private fun to(path: String) = "to(" + toPath(path) + ")"
    private fun include(s1: String, s2: String, s3: String) = "include(from(" + toPath(s1) + "), " +
        "to(" + toPath(s2) + "), " + s3 + ")"

    private fun assertFile(lpr: LaunchProjectResult, path: String, content: String) {
        File(lpr.projectDescription.file.absolutePath, path).let { file ->
            assertThat(file).exists()
            assertThat(file.readText()).isEqualTo(content)
        }
    }

    @Test(description = "Test that copy() and include() work properly")
    fun shouldInstall() {
        val from = from("testFile")
        val to = to("installed")
        val inc = include("a", "deployed2", "glob(\"**/*\")")
        val install = """
                install {
                    copy($from, $to)
                    $inc
                }
            """.trimIndent()
        val bf = BuildFile(listOf("com.beust.kobalt.plugin.packaging.*"), install)
        val testFileContent = "This should be in the file\n"
        val cContent = "Nested file\n"
        val files = listOf(ProjectFile("testFile", testFileContent),
                ProjectFile("a/b/c", cContent))
        val result = launchProject(ProjectInfo(bf, files), arrayOf("install"))

        assertFile(result, "installed/testFile", testFileContent)
        assertFile(result, "deployed2/b/c", cContent)
    }
}
