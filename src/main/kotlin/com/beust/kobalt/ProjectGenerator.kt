package com.beust.kobalt

import com.beust.kobalt.api.IInitContributor
import com.beust.kobalt.api.PluginInfo
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Invoked with --init. Generate a new project.
 */
public class ProjectGenerator @Inject constructor(val pluginInfo: PluginInfo){
    companion object {
        /**
         * Turns a dot property into a proper Kotlin identifier, e.g. common.version -> commonVersion
         */
        fun toIdentifier(key: String): String {
            fun upperFirst(s: String) = if (s.isBlank()) s else s.substring(0, 1).toUpperCase() + s.substring(1)

            return key.split('.').mapIndexed( { index, value -> if (index == 0) value else upperFirst(value) })
                    .joinToString("")
        }
    }

    fun run(args: Args) {
        val contributor = findBestInitContributor(File("."))
        if (contributor != null) {
            contributor.generateBuildFile(FileOutputStream(File(args.buildFile)))
            log(1, "Created ${args.buildFile}")
        } else {
            log(1, "Couldn't identify project, not generating any build file")
        }
    }

    /**
     * Run through all the IInitContributors and return the best one.
     */
    private fun findBestInitContributor(dir: File) : IInitContributor? {
        val result = arrayListOf<Pair<IInitContributor, Int>>()
        pluginInfo.initContributors.forEach {
            it.filesManaged(dir).let { count ->
                if (count > 0) {
                    result.add(Pair(it, count))
                }
            }
        }
        if (result.size > 0) {
            Collections.sort(result, { p1, p2 -> p2.second.compareTo(p1.second) })
            return result[0].first
        } else {
            return null
        }
    }
}

