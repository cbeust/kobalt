package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.File

/**
 * Invoked with --init. Generate a new project.
 */
public class ProjectGenerator @Inject constructor(val pluginInfo: PluginInfo){
    fun run(args: Args) {
        File(args.buildFile).parentFile.mkdirs()
        args.archetypes?.let { archetypes ->
            val contributors = pluginInfo.initContributors.filter { archetypes.contains(it.name) }
            contributors.forEach {
                log(2, "Running archetype ${it.name}")
                it.generateArchetype(args)
            }
        }
    }
}

