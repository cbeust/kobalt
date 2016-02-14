package com.beust.kobalt.app

import com.beust.kobalt.api.IArchetype
import com.beust.kobalt.api.IInitContributor
import com.beust.kobalt.app.java.JavaBuildGenerator
import com.beust.kobalt.app.kotlin.KotlinBuildGenerator
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.misc.log
import com.google.common.collect.ArrayListMultimap

class Archetypes : IInitContributor {
    override val archetypes = listOf(JavaBuildGenerator(), KotlinBuildGenerator())

    fun list(pluginInfo: PluginInfo) {
        val map = ArrayListMultimap.create<String, IArchetype>()
        pluginInfo.initContributors.forEach {
            it.archetypes.forEach {
                map.put(it.pluginName, it)
            }
        }

        log(1, "Available archetypes")
        map.keySet().forEach {
            log(1, "  Plug-in: $it")
            map[it].forEach {
                log(1, "    \"" + it.archetypeName + "\"\t\t" + it.archetypeDescription)
            }
        }
    }
}
