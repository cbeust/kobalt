package com.beust.kobalt.maven

/**
 * Encapsulate a Maven id captured in one string, as used by Gradle or Ivy, e.g. "org.testng:testng:6.9.9".
 * These id's are somewhat painful to manipulate because on top of containing groupId, artifactId
 * and version, they also accept an optional packaging (e.g. "aar") and qualifier (e.g. "no_aop").
 * Determining which is which in an untyped string separated by colons is not clearly defined so
 * this class does a best attempt at deconstructing an id but there's surely room for improvement.
 *
 * This class accepts a versionless id, which needs to end with a :, e.g. "com.beust:jcommander:" (which
 * usually means "latest version") but it doesn't handle version ranges yet.
 */
public class MavenId(val id: String) {
    lateinit var groupId: String
    lateinit var artifactId: String
    var packaging: String? = null
    var version: String? = null

    companion object {
        fun isMavenId(id: String) = with(id.split(":")) {
            size == 3 || size == 4
        }

        fun create(groupId: String, artifactId: String, packaging: String?, version: String?) =
               MavenId(toId(groupId, artifactId, packaging, version))

        fun toId(groupId: String, artifactId: String, packaging: String? = null, version: String?) =
                "$groupId:$artifactId" +
                    (if (packaging != null) ":$packaging" else "") +
                    ":$version"
    }

    init {
        if (! isMavenId(id)) {
            throw IllegalArgumentException("Illegal id: $id")
        }
        val c = id.split(":")
        groupId = c[0]
        artifactId = c[1]
        if (! c[2].isEmpty()) {
            if (isVersion(c[2])) {
                version = c[2]
            } else {
                packaging = c[2]
                version = c[3]
            }
        }
    }

    private fun isVersion(s: String) : Boolean = Character.isDigit(s[0])

    val hasVersion = version != null

    val toId = MavenId.toId(groupId, artifactId, packaging, version)

}
