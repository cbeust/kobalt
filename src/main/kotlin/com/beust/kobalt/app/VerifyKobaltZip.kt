package com.beust.kobalt.app

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.FileInputStream
import java.util.jar.JarFile
import java.util.jar.JarInputStream

class VerifyKobaltZip {
    fun run() {
        var success = true
        var foundKobaltw = false
        var foundJar = false
        var foundWrapperJar = false

        val jarFilePath = "kobalt-" + Kobalt.version + ".jar"
        val zipFilePath = KFiles.joinDir("kobaltBuild", "libs", "kobalt-" + Kobalt.version + ".zip")
        val zipFile = JarFile(zipFilePath)
        val stream = JarInputStream(FileInputStream(zipFilePath))
        var entry = stream.nextEntry
        while (entry != null) {
            if (entry.name == "kobaltw") {
                foundKobaltw = true
            } else if (entry.name.endsWith(jarFilePath)) {
                val ins = zipFile.getInputStream(entry)
                if (ins.available() < 20000000) {
                    throw KobaltException(jarFilePath + " is too small: " + jarFilePath)
                }
                foundJar = true
            } else if (entry.name.endsWith("kobalt-wrapper.jar")) {
                foundWrapperJar = true
            } else {
                success = false
            }
            entry = stream.nextEntry
        }
        if (! success) {
            throw KobaltException("Found unexpected file in $zipFilePath")
        }
        if (! foundKobaltw) {
            throw KobaltException("Couldn't find kobaltw in $zipFilePath")
        }
        if (! foundJar) {
            throw KobaltException("Couldn't find jar in $zipFilePath")
        }
        if (! foundWrapperJar) {
            throw KobaltException("Couldn't find wrapper jar in $zipFilePath")
        }
        log(1, "$zipFilePath looks correct")
    }
}
