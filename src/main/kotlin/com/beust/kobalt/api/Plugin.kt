package com.beust.kobalt.api

import com.beust.kobalt.BasePluginTask
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.internal.TaskResult2
import java.lang.reflect.Method
import java.util.ArrayList

public interface Plugin {
    val name: String
    val tasks : ArrayList<PluginTask>
    fun accept(project: Project) : Boolean
    fun apply(project: Project, context: KobaltContext) {}

    class MethodTask(val method: Method, val taskAnnotation: Task)
    val methodTasks : ArrayList<MethodTask>

    fun addStaticTask(annotation: Task, project: Project, task: (Project) -> TaskResult) {
        tasks.add(object : BasePluginTask(this, annotation.name, annotation.description, project) {
            override fun call(): TaskResult2<PluginTask> {
                val taskResult = task(project)
                return TaskResult2(taskResult.success, this)
            }
        })
    }

    var taskManager : TaskManager

    fun dependsOn(task1: String, task2: String) {
        taskManager.dependsOn(task1, task2)
    }
}
