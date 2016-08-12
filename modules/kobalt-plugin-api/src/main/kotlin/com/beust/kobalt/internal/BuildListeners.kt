package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.AsciiArt
import com.beust.kobalt.api.*
import com.beust.kobalt.misc.kobaltLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Record timings and statuses for tasks and projects and display them at the end of the build.
 */
class BuildListeners : IBuildListener, IBuildReportContributor {
    class ProfilerInfo(val taskName: String, val durationMillis: Long)
    class ProjectInfo(val projectName: String, var durationMillis: Long = 0)

    private val startTimes = ConcurrentHashMap<String, Long>()
    private val timings = arrayListOf<ProfilerInfo>()
    private val projectInfos = hashMapOf<String, ProjectInfo>()
    private var hasFailures = false
    private val args: Args get() = Kobalt.INJECTOR.getInstance(Args::class.java)
    private var buildStartTime: Long? = null

    // IBuildListener
    override fun taskStart(project: Project, context: KobaltContext, taskName: String) {
        startTimes.put(taskName, System.currentTimeMillis())
        if (! projectInfos.containsKey(project.name)) {
            projectInfos.put(project.name, ProjectInfo(project.name))
        }
    }

    // IBuildListener
    override fun taskEnd(project: Project, context: KobaltContext, taskName: String, success: Boolean) {
        if (! success) hasFailures = true
        startTimes[taskName]?.let {
            val taskTime = System.currentTimeMillis() - it
            timings.add(ProfilerInfo(taskName, taskTime))
            projectInfos[project.name]?.let {
                it.durationMillis += taskTime.toLong()
            }
        }
    }

    private val projectStatuses = arrayListOf<Pair<Project, ProjectBuildStatus>>()

    // IBuildListener
    override fun projectStart(project: Project, context: KobaltContext) {
        if (buildStartTime == null) buildStartTime = System.currentTimeMillis()
    }

    // IBuildListener
    override fun projectEnd(project: Project, context: KobaltContext, status: ProjectBuildStatus) {
        projectStatuses.add(Pair(project, status))
    }

    // IBuildReportContributor
    override fun generateReport(context: KobaltContext) {
        fun formatMillis(millis: Long, format: String) = String.format(format, millis.toDouble() / 1000)
        fun formatMillisRight(millis: Long, length: Int) = formatMillis(millis, "%1\$$length.2f")
        fun formatMillisLeft(millis: Long, length: Int) = formatMillis(millis, "%1\$-$length.2f")

        fun millisToSeconds(millis: Long) = (millis.toDouble() / 1000).toInt()

        val profiling = args.profiling
        if (profiling) {
            kobaltLog(1, "\n" + AsciiArt.horizontalSingleLine + " Timings (in seconds)")
            timings.sortedByDescending { it.durationMillis }.forEach {
                kobaltLog(1, formatMillisRight(it.durationMillis, 10) + " " + it.taskName)
            }
            kobaltLog(1, "\n")

        }

        fun col1(s: String) = String.format(" %1\$-30s", s)
        fun col2(s: String) = String.format(" %1\$-13s", s)
        fun col3(s: String) = String.format(" %1\$-8s", s)

        // Only print the build report if there is more than one project and at least one of them failed
        if (timings.any()) {
//        if (timings.size > 1 && hasFailures) {
            val line = listOf(col1("Project"), col2("Build status"), col3("Time"))
                    .joinToString(AsciiArt.verticalBar)
            val table = StringBuffer()
            table.append(AsciiArt.logBox(listOf(line), AsciiArt.bottomLeft2, AsciiArt.bottomRight2, indent = 10) + "\n")
            projectStatuses.forEach { pair ->
                val projectName = pair.first.name
                val cl = listOf(col1(projectName), col2(pair.second.toString()),
                        col3(formatMillisLeft(projectInfos[projectName]!!.durationMillis, 8)))
                        .joinToString(AsciiArt.verticalBar)
                table.append("          " + AsciiArt.verticalBar + " " + cl + " " + AsciiArt.verticalBar + "\n")
            }
            table.append("          " + AsciiArt.lowerBox(line.length))
            kobaltLog(1, table.toString())
//        }
        }

        val buildTime =
            if (buildStartTime != null)
                millisToSeconds(System.currentTimeMillis() - buildStartTime!!)
            else
                0
        // BUILD SUCCESSFUL / FAILED message
        val message =
            if (hasFailures) {
                String.format("BUILD FAILED", buildTime)
            } else if (! args.sequential) {
                val sequentialBuildTime = ((projectInfos.values.sumByDouble { it.durationMillis.toDouble() }) / 1000)
                    .toInt()
                String.format("PARALLEL BUILD SUCCESSFUL (%d SECONDS), sequential build would have taken %d seconds",
                        buildTime, sequentialBuildTime)
            } else {
                String.format("BUILD SUCCESSFUL (%d SECONDS)", buildTime)
            }
        kobaltLog(1, message)

    }

}
