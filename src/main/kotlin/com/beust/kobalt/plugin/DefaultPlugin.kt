package com.beust.kobalt.plugin

import com.beust.kobalt.Plugins
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Dependencies
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.misc.KobaltLogger
import javax.inject.Singleton

/**
 * This plugin is used to gather tasks defined in build files, since these tasks don't really belong to any plugin.
 */
@Singleton
public class DefaultPlugin : BasePlugin(), KobaltLogger {
    companion object {
        public val NAME = "Default"
    }

    override val name: String get() = NAME
}
