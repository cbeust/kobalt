package com.beust.kobalt.internal

import com.beust.kobalt.Args
import org.testng.Assert
import org.testng.annotations.Test
import java.io.File

@Test
class IncrementalManagerTest {
    val TASK = "task"
    val TASK2 = "task2"

    fun shouldSave() {
        val file = File.createTempFile("kobalt-", "")
        println("File: $file")
        val im = IncrementalManager(Args())//, file.absolutePath)
        val v = im.inputChecksumFor(TASK)
        Assert.assertNull(v)
        im.saveInputChecksum(TASK, "44")
        Assert.assertEquals(im.inputChecksumFor(TASK), "44")
        im.saveInputChecksum(TASK, "42")
        Assert.assertEquals(im.inputChecksumFor(TASK), "42")

        im.saveInputChecksum(TASK2, "45")
        Assert.assertEquals(im.inputChecksumFor(TASK2), "45")

        Assert.assertEquals(im.inputChecksumFor(TASK), "42")

        im.saveOutputChecksum(TASK, "49")
        Assert.assertEquals(im.outputChecksumFor(TASK), "49")
    }
}
