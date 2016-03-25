package com.beust.kobalt.maven

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.aether.KobaltAether
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.DependencyExecutor
import com.beust.kobalt.misc.KobaltExecutors
import com.google.inject.Key
import java.util.concurrent.ExecutorService
import javax.inject.Inject

/**
 * Use this class to create instances of `IClasspathDependency` from an id.
 */
class DepFactory @Inject constructor(val localRepo: LocalRepo,
        val executors: KobaltExecutors,
        val aether: KobaltAether) {

    companion object {
        val defExecutor : ExecutorService by lazy {
            Kobalt.INJECTOR.getInstance(Key.get(ExecutorService::class.java, DependencyExecutor::class.java))
        }
    }

    /**
     * Parse the id and return the correct IClasspathDependency
     */
    fun create(id: String, downloadSources: Boolean = false, downloadJavadocs: Boolean = false,
            localFirst : Boolean = true, showNetworkWarning: Boolean = true, executor: ExecutorService = defExecutor)
            : IClasspathDependency {
        if (id.startsWith(FileDependency.PREFIX_FILE)) {
            return FileDependency(id.substring(FileDependency.PREFIX_FILE.length))
        } else {
            val mavenId = MavenId.create(id)
            val result = if (mavenId.hasVersion) aether.create(id)
                else aether.create(id + "(0,]")
            return result
        }
    }
}
