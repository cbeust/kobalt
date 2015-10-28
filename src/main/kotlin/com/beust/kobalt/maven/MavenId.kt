package com.beust.kobalt.maven

public class MavenId(val id: String) {
    lateinit var groupId: String
    lateinit var artifactId: String
    var packaging: String? = null
    var version: String? = null

    companion object {
        fun create(groupId: String, artifactId: String, packaging: String?, version: String?) =
               MavenId(toId(groupId, artifactId, packaging, version))

        fun toId(groupId: String, artifactId: String, packaging: String? = null, version: String?) =
                "$groupId:$artifactId" +
                    (if (packaging != null) ":$packaging" else "") +
                    ":$version"
    }

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

    val toId = MavenId.toId(groupId, artifactId, packaging, version)

}
