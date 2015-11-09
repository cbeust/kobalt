package com.beust.kobalt.maven

import com.beust.kobalt.misc.Strings
import java.io.File

/**
 * Represents a dependency that doesn't have a version: "org.testng:testng:". Such dependencies
 * eventually resolve to the latest version of the artifact.
 */
open public class UnversionedDep(open val groupId: String, open val artifactId: String) {
    open public fun toMetadataXmlPath(fileSystem: Boolean = true): String {
        return toDirectory("", fileSystem) + "maven-metadata.xml"
    }

    /**
     * Turn this dependency to a directory. If fileSystem is true, use the file system
     * dependent path separator, otherwise, use '/' (used to create URL's)
     */
    public fun toDirectory(v: String, fileSystem: Boolean = true): String {
        val sep = if (fileSystem) File.separator else "/"
        val l = listOf(groupId.replace(".", sep), artifactId, v)
        return Strings.Companion.join(sep, l) + "/"
    }
}
