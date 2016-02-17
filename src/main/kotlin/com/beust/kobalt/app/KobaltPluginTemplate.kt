package com.beust.kobalt.app

import com.beust.kobalt.api.IInitContributor
import com.beust.kobalt.plugin.KobaltPlugin

/**
 * Template that generates a Kobalt plug-in project.
 */
class KobaltPluginTemplate : IInitContributor {
    val pluginArchetype = object: JarTemplate("templates/plugin.jar") {
        override val archetypeDescription = "Generate a sample Kobalt plug-in project"

        override val archetypeName = "kobalt-plugin"

        override val pluginName = KobaltPlugin.PLUGIN_NAME
    }

    override val archetypes = listOf(pluginArchetype)
}
