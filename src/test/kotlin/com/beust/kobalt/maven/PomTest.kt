package com.beust.kobalt.maven

import com.beust.kobalt.Args
import com.beust.kobalt.KobaltTest
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.LanguageTemplateGenerator
import com.beust.kobalt.app.ProjectGenerator
import com.beust.kobalt.internal.PluginInfo
import com.google.inject.Inject
import org.testng.Assert
import org.testng.annotations.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class PomTest @Inject constructor() : KobaltTest() {
    @Test
    fun importPom() {
        resourceToFile("PomTest/pom.xml").let { pomSrc ->
            with(Pom("testing", pomSrc)) {
                Assert.assertEquals(groupId, "com.foo.bob")
                Assert.assertEquals(artifactId, "rawr")
                Assert.assertEquals(name, "rawr")
                Assert.assertEquals(version, "1.2.3")
                Assert.assertEquals(properties["commons.version"], "2.1.1")
                Assert.assertEquals(properties["guice.version"], "4.0")

                validateGeneratedFile(this, pomSrc)
            }

        }
    }

    @Test
    fun importBasicPom() {
        resourceToFile("PomTest/pom-norepositories-properties.xml").let { pomSrc ->
            with(Pom("testing", pomSrc)) {
                Assert.assertEquals(groupId, "com.foo.bob")
                Assert.assertEquals(artifactId, "rawr")
                Assert.assertEquals(name, "rawr")
                Assert.assertEquals(version, "1.2.3")
                Assert.assertTrue(properties.isEmpty())
                Assert.assertTrue(repositories.isEmpty())

                validateGeneratedFile(this, pomSrc)
            }
        }
    }

    private fun resourceToFile(fileName: String) : File {
        val ins = javaClass.classLoader.getResourceAsStream(fileName)
        val result = Files.createTempFile("kobaltTest", "").toFile()
        FileOutputStream(result).use {
            ins.copyTo(it)
        }
        return result
    }

    private fun validateGeneratedFile(pom: Pom, pomSrc: File) {
        val temp = File(System.getProperty("java.io.tmpdir"))
        val original = System.getProperty("user.dir")
        System.setProperty("user.dir", temp.absolutePath)

        val pomFile = File(temp, "pom.xml")
        pomFile.deleteOnExit()
        pomSrc.copyTo(pomFile, true)
        try {
            val file = File(temp, "Build.kt")
            file.delete()

            file.deleteOnExit()
            val args = Args()
            args.buildFile = file.absolutePath
            args.templates = "java"

            ProjectGenerator(Kobalt.INJECTOR.getInstance(PluginInfo::class.java)).run(args, javaClass.classLoader)

            val contents = file.readText()
            Assert.assertTrue(contents.contains("group = \"${pom.groupId}\""), "Should find the group defined")
            Assert.assertTrue(contents.contains("name = \"${pom.name}\""), "Should find the name defined")
            Assert.assertTrue(contents.contains("version = \"${pom.version}\""), "Should find the version defined")
            pom.properties.forEach {
                Assert.assertTrue(contents.contains(
                        "val ${LanguageTemplateGenerator.toIdentifier(it.key)} = \"${it.value}\""), "Should find the " +
                        "property defined")
            }
            pom.repositories.forEach {
                Assert.assertTrue(contents.contains(it), "Should find the repository defined")
            }
        } finally {
            System.getProperty("user.dir", original)
        }
    }
}