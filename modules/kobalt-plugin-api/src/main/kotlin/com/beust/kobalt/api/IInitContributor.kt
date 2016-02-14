package com.beust.kobalt.api

import com.beust.kobalt.Args

/**
 * Plugins that want to participate in the --init process (they can generate files to initialize
 * a new project).
 */
interface IInitContributor {
    /**
     * The name of this archetype. This is the name that will be looked up when passed to the --init
     * argument.
     */
    val archetypeName: String

    /**
     * Generate the files for this archetype. The parameter is the arguments that were passed to the kobaltw
     * command.
     */
    fun generateArchetype(args: Args)
}

