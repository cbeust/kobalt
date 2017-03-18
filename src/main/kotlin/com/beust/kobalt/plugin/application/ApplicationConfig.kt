package com.beust.kobalt.plugin.application

import com.beust.kobalt.api.annotation.Directive

class ApplicationConfig {
    @Directive
    var mainClass: String? = null

    @Directive
    fun jvmArgs(vararg args: String) = args.forEach { jvmArgs.add(it) }
    val jvmArgs = arrayListOf<String>()

    @Directive
    fun args(vararg argv: String) = argv.forEach { args.add(it) }
    val args = arrayListOf<String>()
}