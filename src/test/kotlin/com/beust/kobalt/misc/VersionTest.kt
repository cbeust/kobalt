package com.beust.kobalt.misc

import com.beust.kobalt.KobaltTest
import org.testng.Assert
import org.testng.annotations.Test

class VersionTest : KobaltTest() {

    @Test
    fun snapshot() {
        val version = Version.of("1.2.0-SNAPSHOT")
        Assert.assertTrue(version.isSnapshot())
    }

    @Test
    fun rangedVersions() {
        val ranged = Version.of("[2.5,)")
        Assert.assertTrue(ranged.isRangedVersion())
    }

    @Test
    fun selectVersion() {
        var versions = listOf("2.4.public_draft", "2.2", "2.3", "2.4", "2.4-20040521", "2.5", "3.0-alpha-1").map { Version.of(it) }
        Assert.assertEquals(Version.of("[2.5,)").select(versions), Version.of("3.0-alpha-1"))
        Assert.assertEquals(Version.of("[2.5,3.0)").select(versions), Version.of("3.0-alpha-1"))
        Assert.assertEquals(Version.of("[2.6-SNAPSHOT,)").select(versions), Version.of("3.0-alpha-1"))

        versions = listOf("1.0", "1.1", "1.2", "1.2.3", "1.3", "1.4.2", "1.5-SNAPSHOT").map { Version.of(it) }
        Assert.assertEquals(Version.of("[1.2,1.2.3)").select(versions), Version.of("1.2"))
        Assert.assertEquals(Version.of("[1.2,1.2.3]").select(versions), Version.of("1.2.3"))
    }
}