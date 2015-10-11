package com.beust.kobalt.api

import com.beust.kobalt.internal.TaskResult2
import com.beust.kobalt.misc.ToString
import java.util.concurrent.Callable

abstract public class PluginTask : Callable<TaskResult2<PluginTask>> {
    abstract val plugin: Plugin
    open val name: String = ""
    open val doc: String = ""
    abstract val project: Project
    val runBefore = arrayListOf<String>()
    val runAfter = arrayListOf<String>()

    override public fun toString() : String {
        return ToString("PluginTask", "id", project.name + ":" + name).s
    }
}
