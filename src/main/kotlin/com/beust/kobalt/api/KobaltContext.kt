package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.Plugins
import java.util.*

public class KobaltContext(val args: Args) {
    fun findPlugin(name: String) = Plugins.findPlugin(name)
    val classpathContributors: ArrayList<IClasspathContributor> = arrayListOf()
    // sourceContributors
    // projectContributors
    // compilerContributors
}

