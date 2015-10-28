package com.beust.kobalt.maven

import com.beust.kobalt.misc.Strings

open public class SimpleDep(override val groupId: String, override val artifactId: String,
        open val packaging: String?, open val version: String) : UnversionedDep(groupId, artifactId) {
    companion object {
        fun create(id: String) = MavenId(id).let {
            if (id.contains("android")) {
                println("DONOTCOMMIT")
            }
            SimpleDep(it.groupId, it.artifactId, it.packaging, it.version!!)
        }
    }

    override public fun toMetadataXmlPath(fileSystem: Boolean): String {
        return toDirectory(version, fileSystem) + "maven-metadata.xml"
    }

    private fun toFile(v: String, s: String, suffix: String) : String {
        val fv = if (v.contains("SNAPSHOT")) v.replace("SNAPSHOT", "") else v
        return Strings.join("/", arrayListOf(toDirectory(v),
                artifactId + "-" + fv + s + suffix))
    }

    fun toPomFile(v: String) = toFile(v, "", ".pom")

    fun toPomFile(r: RepoFinder.RepoResult) = toFile(r.version, r.snapshotVersion, ".pom")

    fun toJarFile(v: String = version) = toFile(v, "", suffix)

    fun toJarFile(r: RepoFinder.RepoResult) = toFile(r.version, r.snapshotVersion, suffix)

    fun toPomFileName() = "$artifactId-$version.pom"

    val suffix : String
        get() {
            return if (packaging != null && ! packaging.isNullOrBlank()) ".${packaging!!}" else ".jar"
        }
}
