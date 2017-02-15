package com.beust.kobalt

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.JvmCompilerPlugin

@Directive
fun project(vararg projects: Project, init: Project.() -> Unit): Project {
    return Project("").apply {
        init()
        (Kobalt.findPlugin(JvmCompilerPlugin.PLUGIN_NAME) as JvmCompilerPlugin)
                .addDependentProjects(this, projects.toList())
    }
}

@Directive
fun buildScript(init: BuildScriptConfig.() -> Unit): BuildScriptConfig {
    val buildScriptConfig = BuildScriptConfig().apply { init() }
    BUILD_SCRIPT_CONFIG = buildScriptConfig
    return buildScriptConfig
}