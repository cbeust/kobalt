package com.beust.kobalt.plugin.java

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

class JavaConfig(val project: Project) {
    val compilerArgs = arrayListOf<String>()
    @Directive fun args(vararg options: String) = compilerArgs.addAll(options)
}