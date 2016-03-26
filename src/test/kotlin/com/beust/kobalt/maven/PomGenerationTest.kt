package com.beust.kobalt.maven

import com.beust.kobalt.KobaltTest
import com.google.inject.Inject
import org.testng.Assert
import org.testng.annotations.Test

@Test
class PomGenerationTest @Inject constructor(val dependencyManager: DependencyManager): KobaltTest() {
    fun shouldGenerateDependencies() {
        val md = dependencyManager.create("org.testng:testng:6.9.9").toMavenDependencies()
        Assert.assertEquals(md.groupId, "org.testng")
        Assert.assertEquals(md.artifactId, "testng")
        Assert.assertEquals(md.version, "6.9.9")
    }
}
