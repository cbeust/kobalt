package com.beust.kobalt.api

import com.beust.kobalt.BasePluginTask
import com.beust.kobalt.Plugins
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.internal.TaskResult2
import java.util.ArrayList
import java.util.concurrent.Callable
import kotlin.properties.Delegates

abstract public class BasePlugin : Plugin {
    override val tasks: ArrayList<PluginTask> = arrayListOf()
    override var taskManager : TaskManager by Delegates.notNull()
    override var methodTasks = arrayListOf<Plugin.MethodTask>()
    override fun accept(project: Project) = true
    var plugins : Plugins by Delegates.notNull()

    /**
     * Add a dynamic task.
     */
    fun addTask(project: Project, name: String, description: String = "",
            runBefore: List<String> = arrayListOf<String>(),
            runAfter: List<String> = arrayListOf<String>(),
            task: (Project) -> TaskResult) {
        addGenericTask(project, name, description, runBefore, runAfter, task)
    }

}
