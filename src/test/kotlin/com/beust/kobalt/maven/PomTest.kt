package com.beust.kobalt.maven

import org.testng.Assert
import org.testng.annotations.Test
import java.io.File

class PomTest {
    @Test
    fun importPom() {
        val pom = Pom("testing", File("src/test/resources/pom.xml"));

        Assert.assertEquals(pom.groupId, "com.foo.bob")
        Assert.assertEquals(pom.artifactId, "rawr")
        Assert.assertEquals(pom.name, "rawr")
        Assert.assertEquals(pom.version, "1.2.3")
        Assert.assertEquals(pom.properties.get("commons.version"), "2.1.1")
        Assert.assertEquals(pom.properties.get("guice.version"), "4.0")
    }
}