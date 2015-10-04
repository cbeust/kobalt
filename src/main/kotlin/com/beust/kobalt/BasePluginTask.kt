package com.beust.kobalt

import com.beust.kobalt.api.Plugin
import com.beust.kobalt.api.PluginTask
import com.beust.kobalt.api.Project

public abstract class BasePluginTask(override val plugin: Plugin,
        override val name: String,
        override val doc: String,
        override val project: Project)
        : PluginTask {
    override val dependsOn = arrayListOf<String>()
}
