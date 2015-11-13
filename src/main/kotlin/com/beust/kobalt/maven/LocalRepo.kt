package com.beust.kobalt.maven

import com.beust.kobalt.misc.*
import java.io.*
import javax.inject.*

@Singleton
open public class LocalRepo(open val localRepo: String = KFiles.localRepo) {
    init {
        val l = File(localRepo)
        if (!l.exists()) {
            l.mkdirs()
        }
    }

    fun existsPom(d: LocalDep, v: String): Boolean {
        return File(d.toAbsolutePomFile(v)).exists()
    }

    fun existsJar(d: LocalDep, v: String): Boolean {
        return File(d.toAbsoluteJarFilePath(v)).exists()
    }

    /**
     * If the dependency is local, return the correct version for it
     */
    fun findLocalVersion(mavenId: MavenId): String? {
        require(!mavenId.hasVersion) { "mavenId shouldn't have version specified" }
        return findLocalVersion(mavenId.groupId, mavenId.artifactId, mavenId.packaging, mavenId.classifier)
    }

    fun findLocalVersion(groupId: String, artifactId: String, packaging: String? = null, classifier: String? = null): String? {
        // No version: look at all the directories under group/artifactId, pick the latest and see
        // if it contains a maven and jar file
        val dir = toFullPath(KFiles.joinDir(groupId.replace(".", File.separator), artifactId))

        val files = File(dir).listFiles()

        if (files != null) {
            val latestVersion = files.filter { it.isDirectory }
                    .filter { it.name.matches("\\d+(\\.\\d+)*".toRegex()) }
                    .maxBy { Versions.toLongVersion(it.name) }?.name

            if (latestVersion != null) {
                val newDep = LocalDep(MavenId(groupId, artifactId, latestVersion, packaging, classifier), this)
                if (existsPom(newDep, latestVersion) && existsJar(newDep, latestVersion)) {
                    return latestVersion
                }
            }
        }
        return null
    }

    fun toFullPath(path: String): String {
        return localRepo + File.separatorChar + path
    }
}


