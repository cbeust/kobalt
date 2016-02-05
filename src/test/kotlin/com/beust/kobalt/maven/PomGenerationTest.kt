package com.beust.kobalt.maven

import com.beust.kobalt.KobaltTest
import com.beust.kobalt.maven.dependency.MavenDependency
import org.testng.Assert
import org.testng.annotations.Test

@Test
class PomGenerationTest : KobaltTest() {
    fun shouldGenerateDependencies() {
        val md = MavenDependency.create("org.testng:testng:6.9.9").toMavenDependencies()
        Assert.assertEquals(md.groupId, "org.testng")
        Assert.assertEquals(md.artifactId, "testng")
        Assert.assertEquals(md.version, "6.9.9")
    }
}
