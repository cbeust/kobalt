package com.beust.kobalt.maven

import com.beust.kobalt.misc.Strings

open class SimpleDep(open val mavenId: MavenId) : UnversionedDep(mavenId.groupId, mavenId.artifactId) {
    companion object {
        fun create(id: String) = MavenId.create(id).let {
            SimpleDep(it)
        }
    }

    val version: String get() = mavenId.version!!

    private fun toFile(v: String, snapshotVersion: String?, suffix: String) : String {
        val fv = if (v.contains("SNAPSHOT")) v.replace("SNAPSHOT", "") else v
        val result = Strings.join("/", arrayListOf(toDirectory(v, false) +
                artifactId + "-" + fv + (snapshotVersion ?: "") + suffix))
        return result
    }

    fun toPomFile(v: String) = toFile(v, "", ".pom")

    fun toPomFile(r: RepoFinder.RepoResult) = toFile(r.version!!.version, r.snapshotVersion?.version, ".pom")

    fun toJarFile(v: String = version) = toFile(v, "", suffix)

    fun toJarFile(r: RepoFinder.RepoResult) = toFile(r.version!!.version, r.snapshotVersion?.version, suffix)

    fun toPomFileName() = "$artifactId-$version.pom"

    val suffix : String
        get() {
            val packaging = mavenId.packaging
            return if (packaging != null && ! packaging.isNullOrBlank()) ".$packaging" else ".jar"
        }
}
