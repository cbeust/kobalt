package com.beust.kobalt.maven

import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

interface IClasspathDependency {
    companion object {
        val PREFIX_FILE: String = "file:/"
    }

    /** Identifier for this dependency */
    val id: String

    /** Absolute path to the jar file on the local file system */
    val jarFile: Future<File>

    /** Convert to a Maven <dependency> model tag */
    fun toMavenDependencies() : org.apache.maven.model.Dependency

    /** The list of dependencies for this element (not the transitive closure */
    fun directDependencies(): List<IClasspathDependency>

    /** Used to only keep the most recent version for an artifact if no version was specified */
    val shortId: String

    fun transitiveDependencies(executor: ExecutorService) : List<IClasspathDependency> {
        /**
         * All the dependencies we have already downloaded.
         */
        val seen = ConcurrentHashMap<String, String>()

        val thisDep = MavenDependency.create(id, executor)

        var result = ArrayList<IClasspathDependency>(transitiveDependencies(thisDep, seen, executor))
        result.add(thisDep)
        return result
    }

    private fun transitiveDependencies(dep: IClasspathDependency, seen: ConcurrentHashMap<String, String>,
            executor: ExecutorService) : List<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        seen.put(dep.id, dep.id)
        dep.directDependencies().filter {
            ! seen.containsKey(it.id)
        }.forEach {
            seen.put(it.id, it.id)
            val thisDep = MavenDependency.create(it.id, executor)
            result.add(thisDep)
            result.addAll(transitiveDependencies(thisDep, seen, executor))
        }
        return result
    }
}

open public class FileDependency(open val fileName: String) : IClasspathDependency, Comparable<FileDependency> {
    override val id = IClasspathDependency.PREFIX_FILE + fileName

    override val jarFile = CompletedFuture(File(fileName))

    override fun toMavenDependencies(): org.apache.maven.model.Dependency {
        with(org.apache.maven.model.Dependency()) {
            setSystemPath(jarFile.get().getAbsolutePath())
            return this
        }
    }

    override val shortId = fileName

    override fun directDependencies() = arrayListOf<IClasspathDependency>()

    override fun compareTo(other: FileDependency) = fileName.compareTo(other.fileName)
}
