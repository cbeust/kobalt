package com.beust.kobalt.internal

import com.beust.kobalt.TestModule
import com.beust.kobalt.app.BuildFiles
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.io.File

@Guice(modules = arrayOf(TestModule::class))
class BlockExtractorTest {

    @Test
    fun verifyExtraction() {
        val imports = listOf("import com.beust.kobalt.*", "import com . beust.kobalt.api.*")
        val topLines = listOf("", "val VERSION = \"6.11.1-SNAPSHOT\"", "")
        val buildScript = listOf(
                "val bs = buildScript {",
                "  repos(\"https://dl.bintray.com/cbeust/maven\")",
                "}"
        )
        val allLines = imports + topLines + buildScript

        val be = BuildFiles.BLOCK_EXTRACTOR
        val bsi = be.extractBlock(File(""), allLines)
        if (bsi != null) {
            assertThat(bsi.sections.size).isEqualTo(1)
            assertThat(bsi.sections[0].start).isEqualTo(5)
            assertThat(bsi.sections[0].end).isEqualTo(7)
            assertThat(bsi.topLines).isEqualTo(topLines)
        } else {
            throw AssertionError("Should have found a buildScript{}")
        }
    }
}
