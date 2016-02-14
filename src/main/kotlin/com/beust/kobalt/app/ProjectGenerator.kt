package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.api.IInitContributor
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.inject.Inject
import java.io.File

/**
 * Invoked with --init. Generate a new project.
 */
public class ProjectGenerator @Inject constructor(val pluginInfo: PluginInfo){
    fun run(args: Args) {
        File(args.buildFile).parentFile.mkdirs()
        val map = hashMapOf<String, IInitContributor>()
        pluginInfo.initContributors.forEach {
            map.put(it.archetypeName, it)
        }
        args.archetypes?.split(",")?.forEach { archetypeName ->
            val contributor = map[archetypeName]
            if (contributor != null) {
                log(2, "Running archetype $archetypeName")
                contributor.generateArchetype(args)
            } else {
                warn("Couldn't find any archetype named $archetypeName")
            }
        }
    }
}

