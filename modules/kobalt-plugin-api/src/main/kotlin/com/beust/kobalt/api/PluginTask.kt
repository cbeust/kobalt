package com.beust.kobalt.api

import com.beust.kobalt.internal.TaskResult2
import java.util.concurrent.Callable

interface ITask : Callable<TaskResult2<ITask>> {
    val plugin: IPlugin
    val project: Project
    val name: String
    val doc: String
    val group: String
}

abstract class PluginTask : ITask {
    override val name: String = ""
    override open val doc: String = ""
    override open val group: String = "other"

    override fun toString() = project.name + ":" + name
}
