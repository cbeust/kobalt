package com.beust.kobalt.misc

import com.beust.kobalt.IFileSpec
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
        Assert.assertEquals(files[0].path, "modules/wrapper/kobaltBuild/classes/com/beust/kobalt/wrapper/Main.class")
    }
}
