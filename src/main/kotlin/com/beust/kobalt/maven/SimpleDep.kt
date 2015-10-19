package com.beust.kobalt.maven

import com.beust.kobalt.misc.Strings
import com.google.common.base.CharMatcher
import java.io.File
import kotlin.properties.Delegates

open public class SimpleDep(override val groupId: String, override val artifactId: String,
        open val version: String) : UnversionedDep(groupId, artifactId) {
    companion object {
        fun create(id: String) = id.split(":").let { SimpleDep(it[0], it[1], it[2])}
    }

    override public fun toMetadataXmlPath(fileSystem: Boolean): String {
        return toDirectory(version, fileSystem) + "/maven-metadata.xml"
    }

    private fun toFile(v: String, s: String, suffix: String) : String {
        val fv = if (v.contains("SNAPSHOT")) v.replace("SNAPSHOT", "") else v
        return Strings.join("/", arrayListOf(toDirectory(v),
                artifactId + "-" + fv + s + suffix))
    }

    fun toPomFile(v: String) = toFile(v, "", ".pom")

    fun toPomFile(r: RepoFinder.RepoResult) = toFile(r.version, r.snapshotVersion, ".pom")

    fun toJarFile(v: String = version) = toFile(v, "", ".jar")

    fun toJarFile(r: RepoFinder.RepoResult) = toFile(r.version, r.snapshotVersion, ".jar")

    fun toPomFileName() = "${artifactId}-${version}.pom"
}
