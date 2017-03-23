package com.beust.kobalt.internal

import com.beust.kobalt.misc.StringVersion
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.util.*


/**
 * Make sure we parse version numbers correctly.
 */
class StringVersionTest {

    @DataProvider
    fun versionsEqual() : Array<Array<String>>
        = arrayOf(
            arrayOf("1.0", "1"),
            arrayOf("1", "1"),
            arrayOf("1.0.0", "1"),
            arrayOf("1.0", "1.0.0")
    )

    @Test(dataProvider = "versionsEqual")
    fun versionComparisonsEqual(v1: String, v2: String) {
        val sv1 = StringVersion(v1)
        val sv2 = StringVersion(v2)
        assertThat(sv1).isEqualTo(sv2)
        assertThat(sv2).isEqualTo(sv1)
    }

    @DataProvider
    fun versionsNotEqual() : Array<Array<String>>
        = arrayOf(
            arrayOf("0.9", "1"),
            arrayOf("0.9.2", "1"),
            arrayOf("1", "1.2.3"),
            arrayOf("1.2", "1.2.3"),
            arrayOf("1.2.2", "1.2.3"),
            arrayOf("1.2.3", "1.2.4"),
            arrayOf("1", "1.2.3.4"),
            arrayOf("1.2", "1.2.3.4"),
            arrayOf("1.2.3", "1.2.3.4"),
            arrayOf("1.2.3.3", "1.2.3.4"),
            arrayOf("1.2.3.4", "1.2.3.5"),
            arrayOf("4.5.0.201609210915-r", "4.5.0.201609210916-r")
    )

    @Test(dataProvider = "versionsNotEqual")
    fun versionComparisonsNotEqual(v1: String, v2: String) {
        val sv1 = StringVersion(v1)
        val sv2 = StringVersion(v2)
        assertThat(sv1).isLessThan(sv2)
        assertThat(sv2).isGreaterThan(sv1)
        assertThat(sv1).isNotEqualTo(sv2)
    }

    @Test
    fun sortVersions() {
        val versions = listOf("1", "1.2", "0.9", "1.1", "1.1.1", "1.0.2").map(::StringVersion)
        Collections.sort(versions)
        assertThat(versions.map { it.version }).isEqualTo(listOf("0.9", "1", "1.0.2", "1.1", "1.1.1", "1.2"))
    }
}
