package com.beust.kobalt

import com.beust.kobalt.api.IPlugin
import com.beust.kobalt.api.PluginTask
import com.beust.kobalt.api.Project

public abstract class BasePluginTask(override val plugin: IPlugin,
        override val name: String,
        override val doc: String,
        override val project: Project)
        : PluginTask()
