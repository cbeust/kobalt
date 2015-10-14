package com.beust.kobalt.plugin

import com.beust.kobalt.api.BasePlugin
import javax.inject.Singleton

/**
 * This plugin is used to gather tasks defined in build files, since these tasks don't really belong to any plugin.
 */
@Singleton
public class DefaultPlugin : BasePlugin() {
    companion object {
        public val NAME = "Default"
    }

    override val name: String get() = NAME
}
