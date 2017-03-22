package com.beust.kobalt.internal

import com.beust.kobalt.misc.StringVersion
import com.beust.kobalt.misc.Versions
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.DataProvider
import org.testng.annotations.Test


/**
 * Make sure we parse version numbers correctly.
 */
class VersionTest {

    @DataProvider
    fun dp() : Array<Array<Any>>
        = arrayOf(
            arrayOf("0.938", 9380000),
            arrayOf("1.2", 100020000L),
            arrayOf("1.2.3", 100020003L)
    )

    @Test(dataProvider = "dp")
    fun versionConversionShouldWork(version: String, expected: Long) {
        assertThat(Versions.toLongVersion(version)).isEqualTo(expected)
    }

    @DataProvider
    fun versionsEqual() : Array<Array<String>>
        = arrayOf(
            arrayOf("1", "1"),
            arrayOf("1.0", "1"),
            arrayOf("1.0.0", "1"),
            arrayOf("1.0", "1.0.0")
    )

    @Test(dataProvider = "versionsEqual")
    fun versionComparisonsEqual(v1: String, v2: String) {
        assertThat(StringVersion(v1).compareTo(v2)).isEqualTo(StringVersion.Compare.EQ)
        assertThat(StringVersion(v2).compareTo(v1)).isEqualTo(StringVersion.Compare.EQ)
    }

    @DataProvider
    fun versionsNotEqual() : Array<Array<Any>>
        = arrayOf(
            arrayOf("1", "1.2.3", StringVersion.Compare.LT),
            arrayOf("1.2", "1.2.3", StringVersion.Compare.LT),
            arrayOf("1.2.2", "1.2.3", StringVersion.Compare.LT),
            arrayOf("1.2.4", "1.2.3", StringVersion.Compare.GT),
            arrayOf("1", "1.2.3.4", StringVersion.Compare.LT),
            arrayOf("1.2", "1.2.3.4", StringVersion.Compare.LT),
            arrayOf("1.2.3", "1.2.3.4", StringVersion.Compare.LT),
            arrayOf("1.2.3.3", "1.2.3.4", StringVersion.Compare.LT),
            arrayOf("1.2.3.5", "1.2.3.4", StringVersion.Compare.GT)
    )

    @Test(dataProvider = "versionsNotEqual")
    fun versionComparisonsNotEqual(v1: String, v2: String, expected: StringVersion.Compare) {
        assertThat(StringVersion(v1).compareTo(v2)).isEqualTo(expected)
        assertThat(StringVersion(v2).compareTo(v1)).isEqualTo(
                if (expected == StringVersion.Compare.LT) StringVersion.Compare.GT else StringVersion.Compare.LT)
    }
}
