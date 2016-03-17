package com.beust.kobalt.api

import java.util.*

/**
 * Actors that have one config object per project can implement `IConfigActor` by delegating to
 * `ConfigActor`. Then they can easily add and look up configurations per project.
 */
interface IConfigActor<T> {
    val configurations : HashMap<String, T>

    fun configurationFor(project: Project?) = if (project != null) configurations[project.name] else null

    fun addConfiguration(project: Project, configuration: T) = configurations.put(project.name, configuration)
}

open class ConfigActor<T>: IConfigActor<T> {
    override val configurations : HashMap<String, T> = hashMapOf()
}
