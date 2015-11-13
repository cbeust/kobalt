package com.beust.kobalt.maven

open public class SimpleDep(open val mavenId: MavenId) : UnversionedDep(mavenId.groupId, mavenId.artifactId) {
    companion object {
        fun create(id: String) = MavenId(id).let {
            SimpleDep(MavenId(id))
        }
    }

    val version: String get() = mavenId.version!!

    override public fun toMetadataXmlPath(fileSystem: Boolean): String {
        return toDirectory(version, fileSystem) + "maven-metadata.xml"
    }

    private fun toFile(v: String, s: String, classifier: String?, suffix: String) : String {
        val fv = if (v.contains("SNAPSHOT")) v.replace("SNAPSHOT", "") else v
        val classifierPart = if (classifier != null) "-$classifier" else ""

        return listOf(toDirectory(v, false),
                artifactId + "-" + fv + s + classifierPart + suffix).joinToString("/")
    }

    fun toPomFile(v: String) = toFile(v, "", null, ".pom")

    fun toPomFile(r: RepoFinder.RepoResult) = toFile(r.version, r.snapshotVersion, null, ".pom")

    fun toJarFile(v: String = version) = toFile(v, "", mavenId.classifier, suffix)

    fun toJarFile(r: RepoFinder.RepoResult) = toFile(r.version, r.snapshotVersion, mavenId.classifier, suffix)

    fun toPomFileName() = "$artifactId-$version.pom"

    val suffix : String
        get() {
            val packaging = mavenId.packaging
            return if (packaging != null && ! packaging.isNullOrBlank()) ".$packaging" else ".jar"
        }
}
