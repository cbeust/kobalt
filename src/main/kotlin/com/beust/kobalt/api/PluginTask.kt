package com.beust.kobalt.api

import com.beust.kobalt.internal.TaskResult2
import com.beust.kobalt.misc.ToString
import java.util.concurrent.Callable

public interface PluginTask : Callable<TaskResult2<PluginTask>> {
    val plugin: Plugin
    val name: String
    val doc: String
    val project: Project
    val dependsOn : List<String>

    override public fun toString() : String {
        return ToString("PluginTask", "id", project.name + ":" + name).s
    }
}
