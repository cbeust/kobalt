package com.beust.kobalt.api

import com.beust.kobalt.Plugins
import com.beust.kobalt.internal.TaskManager

abstract class BasePlugin : IPlugin {
    lateinit var context: KobaltContext

    override fun accept(project: Project) = true

    override fun apply(project: Project, context: KobaltContext) {
        this.context = context
    }

    override fun shutdown() {}

    override lateinit var taskManager: TaskManager
    lateinit var plugins: Plugins
}
