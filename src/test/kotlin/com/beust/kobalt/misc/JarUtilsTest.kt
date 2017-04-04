package com.beust.kobalt.misc

import com.beust.kobalt.From
import com.beust.kobalt.IFileSpec
import com.beust.kobalt.IncludedFile
import com.beust.kobalt.To
import org.testng.Assert
import org.testng.annotations.Test
import javax.inject.Inject

@Test
class JarUtilsTest @Inject constructor() {

    fun allFromFiles() {
        val inf = IncludedFile(From("kobaltBuild/classes"), To(""),
                listOf(IFileSpec.FileSpec("com/beust/kobalt/wrapper/Main.class")))
        val files = inf.allFromFiles("modules/wrapper")
        println("Files: $files")
        val actual = files[0].path.replace("\\", "/")
        Assert.assertEquals(actual,
                "com/beust/kobalt/wrapper/Main.class")
    }
}
