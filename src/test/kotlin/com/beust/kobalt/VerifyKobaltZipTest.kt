package com.beust.kobalt

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import org.testng.Assert
import org.testng.annotations.Test
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.jar.JarFile
import java.util.jar.JarInputStream

class VerifyKobaltZipTest : KobaltTest() {
    private fun verifyMainJarFile(ins: InputStream) {
        assertExistsInJarInputStream(JarInputStream(ins), "com/beust/kobalt/MainKt.class", "templates/kobaltPlugin.jar")
    }
    @Test
    fun verifySourceJarFile() {
        assertExistsInJar("kobalt-" + Kobalt.version + "-sources.jar", "com/beust/kobalt/Main.kt")
    }

    @Test
    fun verifyZipFile() {
        var success = true
        var foundKobaltw = false
        var foundJar = false
        var foundWrapperJar = false

        val mainJarFilePath = "kobalt-" + Kobalt.version + ".jar"
        val zipFilePath = KFiles.joinDir("kobaltBuild", "libs", "kobalt-" + Kobalt.version + ".zip")
        val zipFile = JarFile(zipFilePath)
        val stream = JarInputStream(FileInputStream(zipFilePath))
        var entry = stream.nextEntry
        while (entry != null) {
            if (entry.name == "kobaltw") {
                foundKobaltw = true
            } else if (entry.name.endsWith(mainJarFilePath)) {
                val ins = zipFile.getInputStream(entry)
                if (ins.available() < 20000000) {
                    throw KobaltException(mainJarFilePath + " is too small: " + mainJarFilePath)
                }
                verifyMainJarFile(ins)
                foundJar = true
            } else if (entry.name.endsWith("kobalt-wrapper.jar")) {
                foundWrapperJar = true
            } else {
                success = false
            }
            entry = stream.nextEntry
        }
        if (! success) {
            throw KobaltException("Found unexpected file in $zipFilePath")
        }
        if (! foundKobaltw) {
            throw KobaltException("Couldn't find kobaltw in $zipFilePath")
        }
        if (! foundJar) {
            throw KobaltException("Couldn't find jar in $zipFilePath")
        }
        if (! foundWrapperJar) {
            throw KobaltException("Couldn't find wrapper jar in $zipFilePath")
        }
        log(1, "$zipFilePath looks correct")
    }

    private fun assertExistsInJarInputStream(ins: JarInputStream, vararg fileNames: String) {
        with(jarContents(ins)) {
            fileNames.forEach { fileName ->
                Assert.assertTrue(contains(fileName), "Couldn't find $fileName")
            }
        }
    }

    private fun assertExistsInJar(jarName: String, vararg fileNames: String) {
        val sourceJarPath = KFiles.joinDir("kobaltBuild", "libs", jarName)
        assertExistsInJarInputStream(JarInputStream(FileInputStream(File(sourceJarPath))), *fileNames)
    }

    private fun jarContents(stream: JarInputStream) : Set<String> {
        val result = hashSetOf<String>()
        var entry = stream.nextEntry
        while (entry != null) {
            result.add(entry.name)
            entry = stream.nextEntry
        }
        return result
    }
}
