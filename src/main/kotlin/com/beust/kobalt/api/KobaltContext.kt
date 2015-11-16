package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.Plugins
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.Variant
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KobaltExecutors

public class KobaltContext(val args: Args) {
    fun findPlugin(name: String) = Plugins.findPlugin(name)
    lateinit var pluginInfo: PluginInfo
    lateinit var pluginProperties: PluginProperties
    lateinit var dependencyManager: DependencyManager
    lateinit var executors: KobaltExecutors
    var variant: Variant = Variant()
}

