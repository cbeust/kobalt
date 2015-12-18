package com.beust.kobalt.api

import com.google.common.collect.ArrayListMultimap

/**
 * A plug-in that has some per-project list of configurations in the build file.
 */
abstract public class ConfigsPlugin<T> : BasePlugin() {
    private val configurations = ArrayListMultimap.create<String, T>()

    fun configurationFor(project: Project) = configurations[project.name]

    fun addConfiguration(project: Project, configuration: T) = configurations.put(project.name, configuration)
}
