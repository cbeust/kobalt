package com.beust.kobalt.app

import com.beust.kobalt.api.ITemplateContributor
import com.beust.kobalt.api.JarTemplate
import com.beust.kobalt.plugin.KobaltPlugin

/**
 * Template that generates a Kobalt plug-in project.
 */
class KobaltPluginTemplate : ITemplateContributor {
    companion object {
        val NAME = "kobaltPlugin"
    }

    val pluginTemplate = object: JarTemplate(ITemplateContributor.DIRECTORY_NAME + "/$NAME/$NAME.jar") {
        override val templateDescription = "Generate a sample Kobalt plug-in project"

        override val templateName = NAME

        override val pluginName = KobaltPlugin.PLUGIN_NAME
    }

    override val templates = listOf(pluginTemplate)
}
