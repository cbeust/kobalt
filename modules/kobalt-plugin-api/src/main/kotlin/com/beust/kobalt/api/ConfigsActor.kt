package com.beust.kobalt.api

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap

/**
 * Actors that have more than config object per project can use this helper class.
 */
interface IConfigsActor<T> {
    val configurations : ListMultimap<String, T>

    fun configurationFor(project: Project?) = if (project != null) configurations[project.name] else null

    fun addConfiguration(project: Project, configuration: T) = configurations.put(project.name, configuration)
}

open class ConfigsActor<T>: IConfigsActor<T> {
    override val configurations = ArrayListMultimap.create<String, T>()
}
