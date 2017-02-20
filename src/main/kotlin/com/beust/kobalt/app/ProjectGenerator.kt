package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.api.FileJarTemplate
import com.beust.kobalt.api.HttpJarTemplate
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

            val finalTemplate: ITemplate? = map[templateName]
                ?: if (File(templateName).exists()) {
                    kobaltLog(2, "Found a template jar file at $templateName, extracting it")
                    object : FileJarTemplate(templateName) {
                        override val templateDescription = "Extract jar template from file"
                        override val templateName = "File template"
                        override val pluginName = ""
                    }
                } else if (templateName.startsWith("http://") || templateName.startsWith("https://")) {
                    object : HttpJarTemplate(templateName) {
                        override val templateDescription = "Extract jar template from HTTP"
                        override val templateName = "HTTP template"
                        override val pluginName = ""
                    }
                } else {
                    warn("Couldn't find any template named $templateName")
                    null
                }

            finalTemplate?.let {
                kobaltLog(2, "Running template $templateName")
                it.generateTemplate(args, classLoader)
                kobaltLog(1, "\n\nTemplate \"$templateName\" installed")
                kobaltLog(1, finalTemplate.instructions)
            }
        }
    }
}

