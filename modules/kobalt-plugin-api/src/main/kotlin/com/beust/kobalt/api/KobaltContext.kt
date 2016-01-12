package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.Plugins
import com.beust.kobalt.Variant
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KobaltExecutors

public class KobaltContext(val args: Args) {
    var variant: Variant = Variant()
    val profiles = arrayListOf<String>()

    init {
        args.profiles?.split(",")?.filterNotNull()?.forEach {
            profiles.add(it)
        }
    }

    fun findPlugin(name: String) = Plugins.findPlugin(name)

    //
    // Injected
    //
    lateinit var pluginInfo: PluginInfo
    lateinit var pluginProperties: PluginProperties
    lateinit var dependencyManager: DependencyManager
    lateinit var executors: KobaltExecutors
    lateinit var settings: KobaltSettings
}

