package com.beust.kobalt.archive

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

/**
 * A jar is exactly like a zip with the addition of a manifest and an optional fatJar boolean.
 */
open class Jar(override val project: Project,
        override var name : String = Archives.defaultArchiveName(project) + ".jar",
        var fatJar: Boolean = false) : Zip(project, name), AttributeHolder {
    @Directive
    fun manifest(init: Manifest.(p: Manifest) -> Unit) : Manifest {
        val m = Manifest(this)
        m.init(m)
        return m
    }

    // Need to specify the version or attributes will just be dropped
    @Directive
    val attributes = arrayListOf(Pair("Manifest-Version", "1.0"))

    override fun addAttribute(k: String, v: String) {
        attributes.add(Pair(k, v))
    }
}


