package com.beust.kobalt.misc

import com.beust.kobalt.IFileSpec
import org.testng.Assert
import org.testng.annotations.Test
import java.io.File

@Test
class IncludedFileTest {
    fun simple() {
        val from = "src/main/kotlin/"
        val inf = IncludedFile(From(from), To(""), listOf(IFileSpec.GlobSpec("**.kt")))
        inf.allFromFiles().map { File(from, it.path) }.forEach {
            Assert.assertTrue(it.exists(), "Should exist: $it")
        }
    }
}
