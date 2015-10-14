package com.beust.kobalt.plugin.apt

import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Dependencies
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.misc.log
import javax.inject.Singleton

@Singleton
public class AptPlugin : BasePlugin() {
    companion object {
        public const val TASK_APT: String = "runApt"
    }

    override val name = "apt"

    @Task(name = TASK_APT, description = "Run apt", runBefore = arrayOf("compile"))
    fun taskApt(project: Project) : TaskResult {
        log(1, "apt called on ${project} with processors ${processors}")
        return TaskResult()
    }

    private val processors = arrayListOf<String>()

    fun addApt(dep: String) {
        processors.add(dep)
    }
}

@Directive
public fun Dependencies.apt(dep: String) {
    (Kobalt.findPlugin("apt") as AptPlugin).addApt(dep)
}