package com.beust.kobalt.plugin.groovy

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

class GroovyConfig(val project: Project) {
    val compilerArgs = arrayListOf<String>()
    @Directive fun args(vararg options: String) = compilerArgs.addAll(options)
}