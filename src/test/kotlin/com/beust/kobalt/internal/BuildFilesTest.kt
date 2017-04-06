package com.beust.kobalt.internal

import com.beust.kobalt.BaseTest
import com.beust.kobalt.BuildFile
import com.beust.kobalt.ProjectFile
import com.beust.kobalt.ProjectInfo
import com.beust.kobalt.misc.KFiles
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Test
import java.io.File

class BuildFilesTest : BaseTest() {

    @Test
    fun shouldGenerateArtifact() {
        val projectInfo = ProjectInfo(
                BuildFile(listOf("com.beust.kobalt.plugin.packaging.*"), "assemble{jar{}}"),
                listOf(ProjectFile("src/main/kotlin/A.kt", "val a = \"foo\"")))

        val result = launchProject(projectInfo, arrayOf("assemble"))

        val project = result.projectDescription
        val jarFile = File(KFiles.joinDir(project.file.absolutePath, "kobaltBuild/libs", project.name + "-"
                + project.version + ".jar"))

        assertThat(jarFile).exists()
    }
}