package com.beust.kobalt.api

import com.beust.kobalt.Args

/**
 * Plugins that want to participate in the --init process (they can generate files to initialize
 * a new project).
 */
interface IInitContributor {
    val name: String

    fun generateArchetype(args: Args)
}

