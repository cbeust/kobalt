package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.api.ITemplate
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.warn
import com.google.inject.Inject
import java.io.File

/**
 * Invoked with --init. Generate a new project.
 */
class ProjectGenerator @Inject constructor(val pluginInfo: PluginInfo){
    fun run(args: Args, classLoader: ClassLoader) {
        File(args.buildFile).parentFile.mkdirs()
        val map = hashMapOf<String, ITemplate>()
        pluginInfo.initContributors.forEach {
            it.templates.forEach {
                map.put(it.templateName, it)
            }
        }

        args.templates?.split(',')?.forEach { templateName ->
            val template = map[templateName]
            if (template != null) {
                kobaltLog(2, "Running template $templateName")
                template.generateTemplate(args, classLoader)
                kobaltLog(1, "\n\nTemplate \"$templateName\" installed")
                kobaltLog(1, template.instructions)
            } else {
                warn("Couldn't find any template named $templateName")
            }
        }
    }
}

