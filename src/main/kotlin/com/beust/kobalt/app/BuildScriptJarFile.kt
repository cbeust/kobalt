package com.beust.kobalt.app

import java.io.File
import java.io.FileWriter

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

    /**
     * @{profiles} is a comma-separated list of profiles, or null
     */
    fun sameProfiles(profiles: String?) : Boolean {
        if (! file.exists()) {
            return profiles == null
        } else {
            val fileContent = file.readText().trim()
            if (fileContent.isEmpty() && profiles == null) {
                return true
            } else if (profiles != null) {
                val savedProfiles = fileContent.split(" ").sorted()
                val expected = profiles.split(",").sorted()
                return savedProfiles == expected
            } else {
                return fileContent.isEmpty()
            }
        }
    }
}

