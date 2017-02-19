package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

class KotlinConfig(val project: Project) {
    val args = arrayListOf<String>()
    fun args(vararg options: String) = args.addAll(options)

    /** The version of the Kotlin compiler */
    @Directive
    var version: String? = null
}