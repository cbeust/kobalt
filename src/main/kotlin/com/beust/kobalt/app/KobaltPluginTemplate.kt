package com.beust.kobalt.app

import com.beust.kobalt.api.ITemplateContributor
import com.beust.kobalt.api.ResourceJarTemplate
import com.beust.kobalt.plugin.KobaltPlugin

/**
 * Template that generates a Kobalt plug-in project.
 */
class KobaltPluginTemplate : ITemplateContributor {
    companion object {
        val NAME = "kobaltPlugin"
    }

    val pluginTemplate = object: ResourceJarTemplate(
            ITemplateContributor.DIRECTORY_NAME + "/$NAME/$NAME.jar",
            this::class.java.classLoader) {
        override val templateDescription = "Generate a sample Kobalt plug-in project"

        override val templateName = NAME

        override val pluginName = KobaltPlugin.PLUGIN_NAME
    }

    override val templates = listOf(pluginTemplate)
}
