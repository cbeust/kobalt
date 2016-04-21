package com.beust.kobalt.api

import com.beust.kobalt.internal.TaskResult2
import java.util.concurrent.Callable

abstract class PluginTask : Callable<TaskResult2<PluginTask>> {
    abstract val plugin: IPlugin
    open val name: String = ""
    open val doc: String = ""
    abstract val project: Project

    override fun toString() : String {
        return project.name + ":" + name
    }
}
