package com.beust.kobalt.api

import com.beust.kobalt.internal.TaskResult2
import java.util.concurrent.Callable

interface ITask : Callable<TaskResult2<ITask>> {
    val plugin: IPlugin
    val project: Project
    val name: String
    val doc: String
}

abstract class PluginTask : ITask {
    override val name: String = ""
    override open val doc: String = ""

    override fun toString() = project.name + ":" + name
}
