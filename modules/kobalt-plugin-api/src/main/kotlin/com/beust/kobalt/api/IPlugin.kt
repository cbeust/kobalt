package com.beust.kobalt.api

import com.beust.kobalt.internal.TaskManager

public interface IPlugin : IPluginActor {
    val name: String
    fun accept(project: Project) : Boolean
    fun apply(project: Project, context: KobaltContext) {}
    var taskManager : TaskManager
}
