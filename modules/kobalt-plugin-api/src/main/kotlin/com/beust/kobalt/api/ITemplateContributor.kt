package com.beust.kobalt.api

import com.beust.kobalt.Args

/**
 * Plugins that want to participate in the --init process (they can generate files to initialize
 * a new project).
 */
interface ITemplateContributor {
    val templates: List<ITemplate>
}

interface ITemplate {
    /**
     * The name of this template. This is the name that will be looked up when passed to the --init
     * argument.
     */
    val templateName: String

    /**
     * Description of this template.
     */
    val templateDescription: String

    /**
     * The plug-in this template belongs to.
     */
    val pluginName: String

    /**
     * Instructions to display to the user after a template has been generated.
     */
    val instructions : String get() = "Build this project with `./kobaltw assemble`"

    /**
     * Generate the files for this template. The parameter is the arguments that were passed to the kobaltw
     * command.
     */
    fun generateTemplate(args: Args, classLoader: ClassLoader)
}

