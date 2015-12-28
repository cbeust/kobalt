package com.beust.kobalt.maven.dependency

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.maven.CompletedFuture
import org.apache.maven.model.Dependency
import java.io.File

open public class FileDependency(open val fileName: String) : IClasspathDependency, Comparable<FileDependency> {
    companion object {
        val PREFIX_FILE: String = "file://"
    }

    override val id = PREFIX_FILE + fileName

    override val jarFile = CompletedFuture(File(fileName))

    override fun toMavenDependencies(): Dependency {
        with(Dependency()) {
            systemPath = jarFile.get().absolutePath
            return this
        }
    }

    override val shortId = fileName

    override fun directDependencies() = arrayListOf<IClasspathDependency>()

    override fun compareTo(other: FileDependency) = fileName.compareTo(other.fileName)

    override fun toString() = fileName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as FileDependency

        if (id != other.id) return false

        return true
    }

    override fun hashCode() = id.hashCode()
}
