package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.internal.ActorUtils
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.File
import java.io.FileOutputStream

/**
 * Invoked with --init. Generate a new project.
 */
public class ProjectGenerator @Inject constructor(val pluginInfo: PluginInfo) {
    companion object {
        /**
         * Turns a dot property into a proper Kotlin identifier, e.g. common.version -> commonVersion
         */
        fun toIdentifier(key: String): String {
            fun upperFirst(s: String) = if (s.isBlank()) s else s.substring(0, 1).toUpperCase() + s.substring(1)

            return key.split('.').mapIndexed({ index, value -> if (index == 0) value else upperFirst(value) })
                    .joinToString("")
        }
    }

    fun run(args: Args) {
        val contributor = ActorUtils.selectAffinityActor(pluginInfo.initContributors, File("."))
        File(args.buildFile).parentFile.mkdirs()
        if (contributor != null) {
            contributor.generateBuildFile(FileOutputStream(File(args.buildFile)))
            log(1, "Created ${args.buildFile}")
        } else {
            log(1, "Couldn't identify project, not generating any build file")
        }
    }
}

