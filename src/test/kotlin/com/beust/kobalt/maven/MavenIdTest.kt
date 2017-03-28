package com.beust.kobalt.maven

import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

@Test
class MavenIdTest {
    @DataProvider
    fun dp() : Array<Array<out Any?>> {
        return arrayOf(
            arrayOf("javax.inject:javax.inject:", "javax.inject", "javax.inject", "(0,]", "jar", null),
            arrayOf("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0.0-beta-1038",
                    "org.jetbrains.kotlin", "kotlin-compiler-embeddable", "1.0.0-beta-1038",
                    "jar", null),
            arrayOf("com.google.inject:guice::no_aop:4.0",
                    "com.google.inject", "guice", "4.0", "jar", "no_aop"),
            arrayOf("com.android.support:appcompat-v7:aar:22.2.1",
                    "com.android.support", "appcompat-v7", "22.2.1", "aar", null)
        )
    }

    @Test
    fun isMavenId() {
        Assert.assertFalse(MavenId.isMavenId("file://C:\\foo\\bar"))
        Assert.assertFalse(MavenId.isMavenId("file:///home/user/foo/bar"))
        Assert.assertFalse(MavenId.isMavenId("com.example:foo"))
        Assert.assertTrue(MavenId.isMavenId("com.example:foo:"))
        Assert.assertTrue(MavenId.isMavenId("com.example:foo:0.5.7"))
        Assert.assertTrue(MavenId.isMavenId("com.example:foo:jar:0.3.0"))
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
