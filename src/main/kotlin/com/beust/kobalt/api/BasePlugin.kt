package com.beust.kobalt.api

import com.beust.kobalt.Plugins
import com.beust.kobalt.internal.TaskManager
import java.util.*
import kotlin.properties.Delegates

abstract public class BasePlugin : Plugin {
    override val tasks: ArrayList<PluginTask> = arrayListOf()
    override var taskManager: TaskManager by Delegates.notNull()
    override var methodTasks = arrayListOf<Plugin.MethodTask>()
    override fun accept(project: Project) = true
    var plugins: Plugins by Delegates.notNull()

    protected val projects = arrayListOf<ProjectDescription>()

    fun addProject(project: Project, dependsOn: Array<out Project>) {
        projects.add(ProjectDescription(project, dependsOn.toList()))
    }

    /**
     * Plugins can expose properties to other plugins through this map.
     * TODO: move this to its own class, it shouldn't be polluting BasePlugin
     */
    val pluginProperties = hashMapOf<String, Any>()

    companion object {
        val BUILD_DIR = "buildDir"
        val LIBS_DIR = "libsDir"
    }
}
