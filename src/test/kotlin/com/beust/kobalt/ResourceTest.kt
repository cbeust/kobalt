package com.beust.kobalt

import org.testng.Assert
import org.testng.annotations.Test
import java.util.Properties

@Test
public class ResourceTest {
    val fileName = "kobalt.properties"

    fun shouldLoadResources() {
        val properties = Properties()
        val res = ClassLoader.getSystemResource(fileName)
        if (res != null) {
            properties.load(res.openStream())
            Assert.assertTrue(properties["foo"] == "bar")
        } else {
            Assert.fail("Couldn't load $fileName")
        }
    }
}
