package com.beust.kobalt.api

import org.apache.maven.model.Dependency
import java.io.File
import java.util.concurrent.Future

/**
 * Encapsulate a dependency that can be put on the classpath. This interface
 * has two subclasses: FileDependency, a physical file, and MavenDependency,
 * which represents a dependency living in a Maven repo.
 *
 * You can instantiate either of these concrete classes with DependencyManager#createMaven
 * and DependencyManager#createFile.
 */
interface IClasspathDependency {
    /** Identifier for this dependency */
    val id: String

    /** Version for this identifier */
    val version: String

    /** Absolute path to the jar file on the local file system */
    val jarFile: Future<File>

    /** Convert to a Maven <dependency> model tag */
    fun toMavenDependencies() : Dependency

    /** The list of dependencies for this element (not the transitive closure) */
    fun directDependencies(): List<IClasspathDependency>

    /** Used to only keep the most recent version for an artifact if no version was specified */
    val shortId: String
}