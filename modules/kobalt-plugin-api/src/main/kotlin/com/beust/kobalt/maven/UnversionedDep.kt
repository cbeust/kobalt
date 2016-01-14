package com.beust.kobalt.maven

import java.io.File

/**
 * Represents a dependency that doesn't have a version: "org.testng:testng:". Such dependencies
 * eventually resolve to the latest version of the artifact.
 */
open class UnversionedDep(open val groupId: String, open val artifactId: String) {
    open fun toMetadataXmlPath(fileSystem: Boolean = true, isLocal: Boolean, version: String? = null) : String {
        var result = toDirectory("", fileSystem) + if (isLocal) "maven-metadata-local.xml" else "maven-metadata.xml"
        if (! File(result).exists() && version != null) {
            result = toDirectory("", fileSystem) + version + File.separator +
                    if (isLocal) "maven-metadata-local.xml" else "maven-metadata.xml"
        }
        return result
    }

    /**
     * Turn this dependency to a directory. If fileSystem is true, use the file system
     * dependent path separator, otherwise, use '/' (used to create URL's).
     */
    fun toDirectory(v: String, fileSystem: Boolean = true, trailingSlash: Boolean = true): String {
        val sep = if (fileSystem) File.separator else "/"
        val l = listOf(groupId.replace(".", sep), artifactId, v)
        val result = l.joinToString(sep)
        return if (trailingSlash && ! result.endsWith(sep)) result + sep else result
    }
}
