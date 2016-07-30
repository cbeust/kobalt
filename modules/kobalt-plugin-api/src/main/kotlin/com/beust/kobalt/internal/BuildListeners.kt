package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.AsciiArt
import com.beust.kobalt.api.*
import com.beust.kobalt.misc.log
import java.util.concurrent.ConcurrentHashMap

/**
 * Record timings and display them at the end of the build.
 */
class BuildListeners : IBuildListener, IBuildReportContributor {
    class ProfilerInfo(val taskName: String, val durationMillis: Long)

    private val startTimes = ConcurrentHashMap<String, Long>()
    private val timings = arrayListOf<ProfilerInfo>()

    override fun taskStart(project: Project, context: KobaltContext, taskName: String) {
        startTimes.put(taskName, System.currentTimeMillis())
    }

    override fun taskEnd(project: Project, context: KobaltContext, taskName: String) {
        startTimes[taskName]?.let {
            timings.add(ProfilerInfo(taskName, System.currentTimeMillis() - it))
        }
    }

    override fun generateReport(context: KobaltContext) {
        val profiling = Kobalt.INJECTOR.getInstance(Args::class.java).profiling
        if (profiling) {
            log(1, "\n" + AsciiArt.horizontalSingleLine + " Timings (in seconds)")
            timings.sortedByDescending { it.durationMillis }.forEach {
                log(1, String.format("%1$10.2f", it.durationMillis.toDouble() / 1000)
                        + " " + it.taskName)
            }
            log(1, "\n")
        }
    }
}
