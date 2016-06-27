package com.beust.kobalt.plugin

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.app.UpdateKobalt
import com.beust.kobalt.misc.CheckVersions
import com.google.inject.Inject
import javax.inject.Singleton

/**
 * This plugin is used to gather tasks defined in build files, since these tasks don't really belong to any plugin.
 */
@Singleton
class KobaltPlugin @Inject constructor(val checkVersions: CheckVersions, val updateKobalt: UpdateKobalt)
        : BasePlugin () {

    companion object {
        val PLUGIN_NAME = "Kobalt"
    }

    override val name: String get() = PLUGIN_NAME

    @Task(name = "checkVersions", description = "Display all the outdated dependencies")
    fun taskCheckVersions(project: Project) : TaskResult {
        checkVersions.run(project)
        return TaskResult()
    }

    @Task(name = "update", description = "Update Kobalt to the latest version")
    fun taskUpdate(project: Project) : TaskResult {
        updateKobalt.updateKobalt()
        return TaskResult()
    }

}
