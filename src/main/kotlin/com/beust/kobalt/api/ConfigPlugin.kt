package com.beust.kobalt.api

/**
 * A plug-in that has some per-project configuration in the build file.
 */
abstract public class ConfigPlugin<T> : BasePlugin() {
    private val configurations = hashMapOf<String, T>()

    fun configurationFor(project: Project) = configurations[project.name]

    fun addConfiguration(project: Project, configuration: T) = configurations.put(project.name, configuration)
}
