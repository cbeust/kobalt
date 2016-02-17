package com.beust.kobalt.api

import com.beust.kobalt.Args

/**
 * Plugins that want to participate in the --init process (they can generate files to initialize
 * a new project).
 */
interface IInitContributor {
    val archetypes: List<IArchetype>
}

interface IArchetype {
    /**
     * The name of this archetype. This is the name that will be looked up when passed to the --init
     * argument.
     */
    val archetypeName: String

    /**
     * Description of this archetype.
     */
    val archetypeDescription: String

    /**
     * The plug-in this archetype belongs to.
     */
    val pluginName: String

    /**
     * Instructions to display to the user after a template has been generated.
     */
    val instructions : String get() = "Build this project with `./kobaltw assemble`"

    /**
     * Generate the files for this archetype. The parameter is the arguments that were passed to the kobaltw
     * command.
     */
    fun generateArchetype(args: Args, classLoader: ClassLoader)
}

