package com.beust.kobalt.app

import java.io.File
import java.io.FileWriter

/**
 * A wrapper around buildScript.jar. Additionally, this class takes care of the "profiles" file that
 * gets saved alongside buildScript.jar and which keeps track of which profiles were active when this
 * jar file gets generated. With this file, Kobalt can accurately decide when the jar file should be
 * regenerated if the user is specifying different profiles than the ones that were used to compile that
 * jar file.
 */
class BuildScriptJarFile(val jarFile: File) {
    val file = File(jarFile.parent, "profiles")

    fun saveProfiles(profiles: String?) {
        if (profiles != null) {
            FileWriter(file).use {
                it.write(profiles.split(",").sorted().joinToString(" "))
            }
        } else {
            file.delete()
        }
    }
}

