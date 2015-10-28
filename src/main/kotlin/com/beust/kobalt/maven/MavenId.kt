package com.beust.kobalt.maven

public class MavenId(val id: String) {
    lateinit var groupId: String
    lateinit var artifactId: String
    var packaging: String? = null
    var version: String? = null

    init {
        val c = id.split(":")
        if (c.size != 3 && c.size != 4) {
            throw IllegalArgumentException("Illegal id: $id")
        }
        groupId = c[0]
        artifactId = c[1]
        packaging = if (c.size == 4) c[2] else null
        version = if (c.size == 4) c[3] else c[2]
    }

    val hasVersion = version != null
}
