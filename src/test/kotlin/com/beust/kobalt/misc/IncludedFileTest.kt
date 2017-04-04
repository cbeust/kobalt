package com.beust.kobalt.misc

import com.beust.kobalt.From
import com.beust.kobalt.IFileSpec
import com.beust.kobalt.IncludedFile
import com.beust.kobalt.To
import org.assertj.core.api.Assertions.assertThat
import org.testng.Assert
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Files

@Test
class IncludedFileTest {
    fun simple() {
        val from = "src/main/kotlin/"
        val inf = IncludedFile(From(from), To(""), listOf(IFileSpec.GlobSpec("**.kt")))
        inf.allFromFiles().map { File(from, it.path) }.forEach {
            Assert.assertTrue(it.exists(), "Should exist: $it")
        }
    }

    fun shouldRecursivelyCopy() {
        val TEXT = "I'm a file"
        val ROOT = Files.createTempDirectory("kobalt-test").toFile()
        val from = File(ROOT, "from")
        File(from, "a/b").apply { mkdirs() }
        val file = File("/a/b/foo")
        File(from, file.path).apply { writeText(TEXT) }
        val to = File(ROOT, "to").apply {
            deleteRecursively()
            mkdirs()
        }

        val targetFile = File(to, file.path)
        assertThat(targetFile).doesNotExist()

        KFiles.copyRecursively(from, to)

        assertThat(to).isDirectory()
        with(targetFile) {
            assertThat(this).exists()
            assertThat(this.readText()).isEqualTo(TEXT)
        }
    }
}
