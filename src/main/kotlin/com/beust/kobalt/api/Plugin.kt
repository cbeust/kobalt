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
        addTask(project, annotation.name, annotation.description, annotation.runBefore.toList(),
                annotation.runAfter.toList(), annotation.wrapAfter.toList(), task)
    }

    fun addTask(project: Project, name: String, description: String = "",
            runBefore: List<String> = arrayListOf<String>(),
            runAfter: List<String> = arrayListOf<String>(),
            wrapAfter: List<String> = arrayListOf<String>(),
            task: (Project) -> TaskResult) {
        tasks.add(
            object : BasePluginTask(this, name, description, project) {
                override fun call(): TaskResult2<PluginTask> {
                    val taskResult = task(project)
                    return TaskResult2(taskResult.success, this)
                }
            })
        runBefore.forEach { taskManager.runBefore(it, name) }
        runAfter.forEach { taskManager.runBefore(name, it) }
        wrapAfter.forEach { taskManager.wrapAfter(it, name)}
    }

    var taskManager : TaskManager
}
