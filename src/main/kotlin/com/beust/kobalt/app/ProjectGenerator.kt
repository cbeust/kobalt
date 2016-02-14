package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.api.IArchetype
import com.beust.kobalt.api.IInitContributor
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.inject.Inject
import java.io.File

/**
 * Invoked with --init. Generate a new project.
 */
class ProjectGenerator @Inject constructor(val pluginInfo: PluginInfo){
    fun run(args: Args) {
        File(args.buildFile).parentFile.mkdirs()
        val map = hashMapOf<String, IArchetype>()
        pluginInfo.initContributors.forEach {
            it.archetypes.forEach {
                map.put(it.archetypeName, it)
            }
        }

        args.archetypes?.split(",")?.forEach { archetypeName ->
            val archetype = map[archetypeName]
            if (archetype != null) {
                log(2, "Running archetype $archetypeName")
                archetype.generateArchetype(args)
            } else {
                warn("Couldn't find any archetype named $archetypeName")
            }
        }
    }
}

