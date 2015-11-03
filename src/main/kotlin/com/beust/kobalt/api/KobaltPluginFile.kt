package com.beust.kobalt.api

import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.plugin.java.JavaPlugin
import com.beust.kobalt.plugin.kotlin.KotlinPlugin
import java.util.*

class ProjectDescription(val project: Project, val dependsOn: List<Project>)

interface IProjectContributor {
    fun projects() : List<ProjectDescription>
}

/**
 * Implement this interface in order to add your own entries to the classpath. A list of contributors
 * can be found on the `KobaltContext`.
 */
interface IClasspathContributor {
    fun entriesFor(project: Project) : Collection<IClasspathDependency>
}

class KobaltPluginFile {
    fun <T> instanceOf(c: Class<T>) : T = Kobalt.INJECTOR.getInstance(c)

    val projectContributors : ArrayList<Class<out IProjectContributor>> =
            arrayListOf(JavaPlugin::class.java, KotlinPlugin::class.java)

    val classpathContributors: ArrayList<Class<out IClasspathContributor>> =
            arrayListOf(KotlinPlugin::class.java)

    // Future contributors:
    // compilerArgs
    // source files
    // compilers
    // --init
}