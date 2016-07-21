package com.beust.kobalt.maven

import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.Versions
import com.google.inject.Inject
import java.io.File
import java.util.*
import javax.inject.Singleton

@Singleton
open class LocalRepo @Inject constructor(val kobaltSettings: KobaltSettings) {
    val localRepo: File
        get() = kobaltSettings.localCache

    fun existsPom(d: LocalDep, v: String) : Boolean {
        return File(d.toAbsolutePomFile(v)).exists()
    }

    fun existsJar(d: LocalDep, v: String) : Boolean {
        return File(d.toAbsoluteJarFilePath(v)).exists()
    }

    /**
     * If the dependency is local, return the correct version for it
     */
    fun findLocalVersion(groupId: String, artifactId: String, packaging: String? = null) : String? {
        // No version: look at all the directories under group/artifactId, pick the latest and see
        // if it contains a maven and jar file
        val dir = toFullPath(KFiles.joinDir(groupId.replace(".", File.separator), artifactId))
        val files = File(dir).listFiles()

        if (files != null) {
            val directories = files.filter { it.isDirectory }
            if (directories.size > 0) {
                Collections.sort(directories, { f1, f2 ->
                    val v1 = Versions.toLongVersion(f1.name)
                    val v2 = Versions.toLongVersion(f2.name)
                    v2.compareTo(v1) // we want the most recent at position 0
                })
                val result = directories[0].name
                val newDep = LocalDep(MavenId.create(groupId, artifactId, packaging, null, result), this)
                if (existsPom(newDep, result) && existsJar(newDep, result)) {
                    return result
                }
            }
        }
        return null
    }

    fun toFullPath(path: String) = File(localRepo, path).absolutePath
}


