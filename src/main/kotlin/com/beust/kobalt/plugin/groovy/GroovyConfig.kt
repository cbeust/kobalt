package com.beust.kobalt.plugin.groovy

import com.beust.kobalt.api.Project

class GroovyConfig(val project: Project) {
    val compilerArgs = arrayListOf<String>()
    fun args(vararg options: String) = compilerArgs.addAll(options)
}