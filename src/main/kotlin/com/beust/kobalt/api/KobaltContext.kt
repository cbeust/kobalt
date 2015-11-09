package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.Plugins
import com.beust.kobalt.internal.PluginInfo

public class KobaltContext(val args: Args) {
    fun findPlugin(name: String) = Plugins.findPlugin(name)
    lateinit var pluginInfo: PluginInfo
    lateinit var pluginProperties: PluginProperties
}

