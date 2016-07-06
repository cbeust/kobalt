package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.Plugins
import com.beust.kobalt.Variant
import com.beust.kobalt.internal.IncrementalManager
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KobaltExecutors
import java.io.File

class KobaltContext(val args: Args) {
    lateinit var variant: Variant
    val profiles = arrayListOf<String>()

    init {
        args.profiles?.split(',')?.filterNotNull()?.forEach {
            profiles.add(it)
        }
    }

    fun findPlugin(name: String) = Plugins.findPlugin(name)

    /** All the projects that are being built during this run */
    val allProjects = arrayListOf<Project>()

    /** For internal use only */
    val internalContext = InternalContext()

    //
    // Injected
    //
    lateinit var pluginInfo: PluginInfo
    lateinit var pluginProperties: PluginProperties
    lateinit var dependencyManager: DependencyManager
    lateinit var executors: KobaltExecutors
    lateinit var settings: KobaltSettings
    lateinit var incrementalManager: IncrementalManager
}

class InternalContext {
    /**
     * When an incremental task decides it's up to date, it sets this boolean to true so that subsequent
     * tasks in that project can be skipped as well. This is an internal field that should only be set by Kobalt.
     */
    private val incrementalSuccesses = hashSetOf<String>()
    fun previousTaskWasIncrementalSuccess(projectName: String) = incrementalSuccesses.contains(projectName) ?: false
    fun setIncrementalSuccess(projectName: String) = incrementalSuccesses.add(projectName)

    /**
     * Keep track of whether the build file was modified. If this boolean is true, incremental compilation
     * will be disabled.
     */
    var buildFileOutOfDate: Boolean = false

    /**
     * The absolute directory of the current project.
     */
    var absoluteDir: File? = null
}