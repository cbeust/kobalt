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
data class MavenId(val groupId: String, val artifactId: String, val version: String?, val packaging: String? = null, val classifier: String? = null) {

    companion object {
        fun isMavenId(id: String) = with(id.split(":")) {
            size in 2..5
        }

        @Deprecated("", ReplaceWith("MavenId(groupId, artifactId, version, packaging)", "com.beust.kobalt.maven.MavenId"))
        fun create(groupId: String, artifactId: String, packaging: String?, version: String?) =
                MavenId(groupId, artifactId, version, packaging)

        fun toId(groupId: String, artifactId: String, packaging: String? = null, version: String?, classifier: String? = null) =
                listOf(groupId, artifactId, packaging, version, classifier).filterNotNull().joinToString(":")
    }

    val hasVersion: Boolean
        get() = version != null

    val id: String
        get() = MavenId.toId(groupId, artifactId, packaging, version, classifier)

}

fun MavenId(id: String): MavenId {
    if (!isMavenId(id)) {
        throw IllegalArgumentException("Illegal id: $id")
    }
    val c = id.split(":").toLinkedList()
    val (groupId, artifactId) = c
    c.removeFirst()
    c.removeFirst()

    val packaging = if (c.isNotEmpty() && !isVersion(c.first()) && c.first().isNotEmpty()) {
        c.removeFirst()!!
    } else {
        null
    }

    val version = if (c.isNotEmpty() && isVersion(c.first()) && c.first().isNotEmpty()) {
        c.removeFirst()!!
    } else {
        null
    }

    val classifier = if (c.isNotEmpty() && c.first().isNotEmpty()) {
        c.removeFirst()!!
    } else null

    return MavenId(groupId, artifactId, version, packaging, classifier)
}

private fun isVersion(s: String): Boolean = s.isNotEmpty() && Character.isDigit(s[0])
