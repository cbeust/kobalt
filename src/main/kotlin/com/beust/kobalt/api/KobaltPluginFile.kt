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

/**
 * All the information gathered from the various plugin.xml that were collected.
 */
class PluginInfoDescription {
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

/**
 * Turn the classes found in PluginInfoDescription into concrete objects that plugins can then use.
 */
class PluginInfo(val description: PluginInfoDescription) {
    val projectContributors = arrayListOf<IProjectContributor>()
    val classpathContributors = arrayListOf<IClasspathContributor>()

    init {
        classpathContributors.addAll(description.classpathContributors.map { description.instanceOf(it) })
        projectContributors.addAll(description.projectContributors.map { description.instanceOf(it) })
    }
}