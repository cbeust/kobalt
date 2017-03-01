package com.beust.kobalt.internal

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
}
