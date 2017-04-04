package com.beust.kobalt

import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Files

class IncludeExcludeTest : KobaltTest() {
    private lateinit var topDirectory: File
    private lateinit var directory: File
    private lateinit var htmlDir: File

    val A1 = "A1.class"
    val B1 = "B1.class"
    val B2 = "B2.class"
    val C1 = "C1.class"
    val C2 = "C2.class"
    val C3 = "C3.class"
    val A_HTML = "a.html"

    @BeforeClass
    fun bc() {
        topDirectory = Files.createTempDirectory("kobaltTest-").toFile()
        directory = File(topDirectory, "com/beust")
        directory.mkdirs()
        listOf(A1, B1, B2, C1, C2, C3).forEach {
            File(directory, it).createNewFile()
        }

        htmlDir = Files.createTempDirectory("kobaltTest-").toFile()
        htmlDir.mkdirs()
        File(htmlDir, A_HTML).createNewFile()
    }

    @Test
    fun html() {
        val inc = IncludedFile(From(""), To(""), listOf(IFileSpec.GlobSpec("**html")))
        val files = inc.allFromFiles(htmlDir.path)
        Assert.assertEquals(files.size, 1)
        Assert.assertEquals(files[0].path, A_HTML)
    }

    @DataProvider
    fun dp() : Array<Array<out Any?>> = arrayOf(
            arrayOf(directory, listOf("**/A*.class", "**/B*.class"), listOf<String>(), listOf(A1, B1, B2)),
            arrayOf(directory, listOf("**/A*class", "**/B*class"), listOf("**/B*class"), listOf(A1)),
            arrayOf(directory, listOf("**/*class"), listOf("**/B*class"), listOf(A1, C1, C2, C3)),
            arrayOf(topDirectory, listOf("**/*class"), listOf<String>(), listOf(A1, B1, B2, C1, C2, C3)),
            arrayOf(topDirectory, listOf("*class"), listOf<String>(), listOf<String>()),
            arrayOf(topDirectory, listOf("**/B*class"), listOf<String>(), listOf(B1, B2)),
            arrayOf(topDirectory, listOf("**/A*class", "**/B*class"), listOf("**/C*class"),
                    listOf(A1, B1, B2)),
            arrayOf(topDirectory, listOf("**/A*class", "**/B*class"), listOf("**/B*class"),
                listOf(A1))
    )

    @Test(dataProvider = "dp")
    fun shouldInclude(root: File, includedSpec: List<String>, excludedSpec: List<String>, expectedFiles:
    List<String>) {
        val g = IFileSpec.GlobSpec(includedSpec)
        val files = g.toFiles(null, root.path, excludedSpec.map { Glob(it) })
//        if (files.map { it.name } != expectedFiles) {
//            println("FAILURE")
//            val files2 = g.toFiles(null, root.path, excludedSpec.map { Glob(it) })
//        }
        Assert.assertEquals(files.map { it.name }.toHashSet(), expectedFiles.toHashSet())
    }
}
