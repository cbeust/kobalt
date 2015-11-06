package com.beust.kobalt.plugin.application

import com.beust.kobalt.Plugins
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KobaltExecutors
import com.google.inject.Inject
import com.google.inject.Singleton

@Directive
class ApplicationConfig {
    var mainClass: String? = null
    var jvmArgs = arrayListOf<String>()

    fun jvmArgs(vararg args: String) = args.forEach { jvmArgs.add(it) }
}

@Directive
fun Project.application(init: ApplicationConfig.() -> Unit) {
    ApplicationConfig().let { config ->
        config.init()
        (Plugins.findPlugin(ApplicationPlugin.NAME) as ApplicationPlugin).addConfig(this, config)
    }
}

@Singleton
class ApplicationPlugin @Inject constructor(val dependencyManager : DependencyManager,
        val executors: KobaltExecutors, val localRepo: LocalRepo) : BasePlugin() {

    companion object {
        const val NAME = "application"
    }

    override val name = NAME

    fun addConfig(project: Project, config: ApplicationConfig) {
        println("Adding config $config")
    }
}

