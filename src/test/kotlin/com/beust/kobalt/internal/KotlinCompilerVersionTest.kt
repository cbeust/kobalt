package com.beust.kobalt.internal

import com.beust.kobalt.BaseTest
import com.beust.kobalt.BuildFile
import com.beust.kobalt.ProjectFile
import com.beust.kobalt.ProjectInfo
import com.beust.kobalt.misc.KFiles
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Test
import java.io.File

class KotlinCompilerVersionTest : BaseTest() {

    @Test
    fun shouldCompileWithExternalKotlin() {
        val projectInfo = ProjectInfo(
                BuildFile(
                        listOf("com.beust.kobalt.plugin.packaging.*", "com.beust.kobalt.plugin.kotlin.kotlinCompiler"),
                        """
                            kotlinCompiler {
                                version = "1.2.60"
                                args("-jvm-target", "1.8")
                            }
                            assemble{ jar{} }
                            """
                ),
                listOf(
                        ProjectFile("src/main/kotlin/A.kt", "val a = Bob()"),
                        ProjectFile("src/main/kotlin/Bob.java", "class Bob {  }")
                )
        )

        val result = launchProject(projectInfo, arrayOf("assemble"))

        val project = result.projectDescription
        val jarFile = File(KFiles.joinDir(project.file.absolutePath, "kobaltBuild/libs", project.name + "-"
                + project.version + ".jar"))

        assertThat(jarFile).exists()
    }

    @Test
    fun shouldFailWhenKotlinVersionDoesNotExist() {
        val projectInfo = ProjectInfo(
                BuildFile(
                        listOf("com.beust.kobalt.plugin.packaging.*", "com.beust.kobalt.plugin.kotlin.kotlinCompiler"),
                        """
                            kotlinCompiler { version = "1.1.20" }
                            assemble{ jar{} }
                            """
                ),
                listOf(ProjectFile("src/main/kotlin/A.kt", "val a = \"foo\"")))

        val result = launchProject(projectInfo, arrayOf("assemble"))

        assertThat(result).isEqualTo(1)
    }
}
