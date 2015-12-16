package com.beust.kobalt.maven

import com.beust.kobalt.Args
import com.beust.kobalt.KobaltTest
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.ProjectGenerator
import com.beust.kobalt.internal.PluginInfo
import com.google.inject.Inject
import org.testng.Assert
import org.testng.annotations.Test
import java.io.File

class PomTest @Inject constructor() : KobaltTest() {
    @Test
    fun importPom() {
        val pomSrc = File("src/test/resources/pom.xml")
        val pom = Pom("testing", pomSrc);

        Assert.assertEquals(pom.groupId, "com.foo.bob")
        Assert.assertEquals(pom.artifactId, "rawr")
        Assert.assertEquals(pom.name, "rawr")
        Assert.assertEquals(pom.version, "1.2.3")
        Assert.assertEquals(pom.properties.get("commons.version"), "2.1.1")
        Assert.assertEquals(pom.properties.get("guice.version"), "4.0")

        validateGeneratedFile(pom, pomSrc)
    }

    @Test
    fun importBasicPom() {
            val pomSrc = File("src/test/resources/pom-norepositories-properties.xml")
            val pom = Pom("testing", pomSrc);

            Assert.assertEquals(pom.groupId, "com.foo.bob")
            Assert.assertEquals(pom.artifactId, "rawr")
            Assert.assertEquals(pom.name, "rawr")
            Assert.assertEquals(pom.version, "1.2.3")
            Assert.assertTrue(pom.properties.isEmpty())
            Assert.assertTrue(pom.repositories.isEmpty())

        validateGeneratedFile(pom, pomSrc)
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
            args.init = true

            ProjectGenerator(Kobalt.INJECTOR.getInstance(PluginInfo::class.java)).run(args)

            var contents = file.readText()
            Assert.assertTrue(contents.contains("group = \"${pom.groupId}\""), "Should find the group defined")
            Assert.assertTrue(contents.contains("name = \"${pom.name}\""), "Should find the name defined")
            Assert.assertTrue(contents.contains("version = \"${pom.version}\""), "Should find the version defined")
            pom.properties.forEach {
                Assert.assertTrue(contents.contains("val ${ProjectGenerator.toIdentifier(it.key)} = \"${it.value}\""), "Should find the " +
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