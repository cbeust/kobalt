package com.beust.kobalt

import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import org.testng.Assert
import org.testng.annotations.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.InputStream
import java.util.*
import java.util.jar.JarFile
import java.util.jar.JarInputStream

class VerifyKobaltZipTest : KobaltTest() {
    @Test
    fun verifySourceJarFile() {
        assertExistsInJar("kobalt-$KOBALT_VERSION-sources.jar", "com/beust/kobalt/Main.kt")
    }

    @Test
    fun verifyZipFile() {
        var foundKobaltw = false
        var foundJar = false
        var foundWrapperJar = false

        val mainJarFilePath = "kobalt-$KOBALT_VERSION.jar"
        val zipFilePath = KFiles.joinDir("kobaltBuild", "libs", "kobalt-$KOBALT_VERSION.zip")
        if (File(zipFilePath).exists()) {
            val zipFile = JarFile(zipFilePath)
            val stream = JarInputStream(FileInputStream(zipFilePath))
            var entry = stream.nextEntry
            while (entry != null) {
                if (entry.name.endsWith("kobaltw")) {
                    foundKobaltw = true
                } else if (entry.name.endsWith(mainJarFilePath)) {
                    val ins = zipFile.getInputStream(entry)
                    if (ins.available() < 20000000) {
                        throw KobaltException(mainJarFilePath + " is too small: " + mainJarFilePath)
                    }
                    verifyMainJarFile(ins)
                    foundJar = true
                } else if (entry.name.endsWith("kobalt-wrapper.jar")) {
                    val ins = zipFile.getInputStream(entry)
                    foundWrapperJar = true
                    assertExistsInJar(JarInputStream(ins), "kobalt.properties")
                }
                entry = stream.nextEntry
            }
            if (!foundKobaltw) {
                throw KobaltException("Couldn't find kobaltw in $zipFilePath")
            }
            if (!foundJar) {
                throw KobaltException("Couldn't find jar in $zipFilePath")
            }
            if (!foundWrapperJar) {
                throw KobaltException("Couldn't find wrapper jar in $zipFilePath")
            }
            kobaltLog(1, "$zipFilePath looks correct")
        } else {
            kobaltLog(1, "Couldn't find $zipFilePath, skipping test")
        }
    }

    // Can't use Kobalt.version since the tests have their own src/test/resources/kobalt.properties
    val KOBALT_VERSION: String
        get() {
            val p = Properties()
            p.load(FileReader("src/main/resources/kobalt.properties"))
            val result = p.getProperty("kobalt.version")
            return result
        }

    private fun verifyMainJarFile(ins: InputStream) {
        JarInputStream(ins).let { jar ->
            assertExistsInJar(jar, "com/beust/kobalt/MainKt.class",
                    "templates/kobaltPlugin/kobaltPlugin.jar", "com/beust/kobalt/Args.class",
                    "com/beust/kobalt/wrapper/Main.class")
            assertDoesNotExistInJar(jar, "BuildKt.class")
        }
    }

    private fun assertExistsInJar(ins: JarInputStream, vararg fileNames: String)
        = assertExistence(ins, true, *fileNames)

    private fun assertDoesNotExistInJar(ins: JarInputStream, vararg fileNames: String)
        = assertExistence(ins, false, *fileNames)

    private fun assertExistence(ins: JarInputStream, verifyExistence: Boolean, vararg fileNames: String) {
        with(jarContents(ins)) {
            fileNames.forEach { fileName ->
                if (verifyExistence) {
                    Assert.assertTrue(contains(fileName), "Couldn't find $fileName")
                } else {
                    Assert.assertFalse(contains(fileName), "Couldn't find $fileName")
                }
            }
        }
    }

    private fun assertExistsInJar(jarName: String, vararg fileNames: String) {
        val sourceJarPath = KFiles.joinDir("kobaltBuild", "libs", jarName)
        val file = File(sourceJarPath)
        if (file.exists()) {
            assertExistsInJar(JarInputStream(FileInputStream(file)), *fileNames)
        } else {
            kobaltLog(1, "Couldn't find $file, skipping test")
        }
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
