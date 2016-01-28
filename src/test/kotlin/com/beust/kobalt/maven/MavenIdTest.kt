package com.beust.kobalt.maven

import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

@Test
class MavenIdTest {
    @DataProvider
    fun dp() : Array<Array<out Any?>> {
        return arrayOf(
            arrayOf("javax.inject:javax.inject:", "javax.inject", "javax.inject", null, null, null),
            arrayOf("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0.0-beta-1038",
                    "org.jetbrains.kotlin", "kotlin-compiler-embeddable", "1.0.0-beta-1038",
                    null, null),
            arrayOf("com.google.inject:guice:4.0:no_aop",
                    "com.google.inject", "guice", "4.0", null, "no_aop"),
            arrayOf("com.android.support:appcompat-v7:22.2.1@aar",
                    "com.android.support", "appcompat-v7", "22.2.1", "aar", null)
        )
    }

    @Test(dataProvider = "dp")
    fun parseVersions(id: String, groupId: String, artifactId: String, version: String?,
            packaging: String?, qualifier: String?) {
        val mi = MavenId.create(id)
        Assert.assertEquals(mi.groupId, groupId)
        Assert.assertEquals(mi.artifactId, artifactId)
        Assert.assertEquals(mi.version, version)
        Assert.assertEquals(mi.packaging, packaging)
//        Assert.assertEquals(mi.qualifier, qualifier)
    }

    fun versionWithVCharacter() {
        val mi = MavenId.createNoInterceptors("com.github.AntennaPod:AntennaPod-AudioPlayer:v1.0.9")
        Assert.assertEquals(mi.version, "v1.0.9")
    }
}
