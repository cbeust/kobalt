package com.beust.kobalt.internal

import com.beust.kobalt.IncrementalTaskInfo
import com.beust.kobalt.TaskResult
import com.beust.kobalt.Variant
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

data class TaskInfo(val taskName: String, var inputChecksum: String? = null, var outputChecksum: String? = null)

class BuildInfo(var tasks: List<TaskInfo>)

/**
 * Manage the file .kobalt/build-info.kt, which keeps track of input and output checksums to manage
 * incremental builds.
 */
class IncrementalManager(val fileName: String = IncrementalManager.BUILD_INFO_FILE) {
    companion object {
        val BUILD_INFO_FILE = KFiles.joinDir(KFiles.KOBALT_DOT_DIR, "buildInfo.json")
    }

    private fun buildInfo() = File(fileName).let { file ->
        if (file.exists()) {
            Gson().fromJson(FileReader(file), BuildInfo::class.java) ?: BuildInfo(emptyList())
        } else {
            BuildInfo(emptyList())
        }
    }

    private fun taskInfos() = hashMapOf<String, TaskInfo>().apply {
        buildInfo().tasks.forEach {
            put(it.taskName, it)
        }
    }

    private fun save(map: Map<String, TaskInfo>) {
        val bi = BuildInfo(map.values.toList())
        val json = GsonBuilder().setPrettyPrinting().create().toJson(bi)
        Files.write(Paths.get(fileName), json.toByteArray(Charset.defaultCharset()))
    }

    private fun taskInfoFor(taskInfos: HashMap<String, TaskInfo>, taskName: String)
            = taskInfos.getOrPut(taskName, { -> TaskInfo(taskName) })

    fun saveInputChecksum(taskName: String, inputChecksum: String) {
        with(taskInfos()) {
            taskInfoFor(this, taskName).inputChecksum = inputChecksum
            save(this)
        }
    }

    fun inputChecksumFor(taskName: String) : String? =
            taskInfoFor(taskInfos(), taskName).inputChecksum

    fun saveOutputChecksum(taskName: String, outputChecksum: String) {
        with(taskInfos()) {
            taskInfoFor(this, taskName).outputChecksum = outputChecksum
            save(this)
        }
    }

    fun outputChecksumFor(taskName: String) : String? =
            taskInfoFor(taskInfos(), taskName).outputChecksum
    /**
     * @param method is assumed to return an IncrementalTaskInfo.
     * @return a closure that invokes that method and decide whether to run the task or not based
     * on the content of that IncrementalTaskInfo
     */
    fun toIncrementalTaskClosure(shortTaskName: String, method: (Project) -> IncrementalTaskInfo,
            variant: Variant = Variant())
            : (Project) -> TaskResult {
        return { project: Project ->
            Kobalt.context?.variant = variant
            val iit = method(project)
            val taskName = project.name + ":" + shortTaskName
            var upToDate = false
            var taskOutputChecksum : String? = null
            inputChecksumFor(taskName)?.let { inputChecksum ->
                val dependsOnDirtyProjects = project.projectExtra.dependsOnDirtyProjects(project)
                if (inputChecksum == iit.inputChecksum && ! dependsOnDirtyProjects) {
                    outputChecksumFor(taskName)?.let { outputChecksum ->
                        taskOutputChecksum = iit.outputChecksum()
                        if (outputChecksum == taskOutputChecksum) {
                            upToDate = true
                        } else {
                            logIncremental(2, "Incremental task $taskName output is out of date, running it")
                        }
                    }
                } else {
                    if (dependsOnDirtyProjects) {
                        logIncremental(2, "Project ${project.name} depends on dirty project, running $taskName")
                    } else {
                        logIncremental(2, "Incremental task $taskName input is out of date, running it"
                                + " old: $inputChecksum new: ${iit.inputChecksum}")
                    }
                    project.projectExtra.isDirty = true
                }
            }
            if (! upToDate) {
                val result = iit.task(project)
                if (result.success) {
                    logIncremental(2, "Incremental task $taskName done running, saving checksums")
                    iit.inputChecksum?.let {
                        saveInputChecksum(taskName, it)
                        logIncremental(2, "          input checksum \"$it\" saved")
                    }
                    // Important to rerun the checksum here since the output of the task might have changed it
                    iit.outputChecksum()?.let {
                        saveOutputChecksum(taskName, it)
                        logIncremental(2, "          output checksum \"$it\" saved")
                    }
                }
                result
            } else {
                logIncremental(2, "Incremental task \"$taskName\" is up to date, not running it")
                TaskResult()
            }
        }
    }

    private fun logIncremental(level: Int, s: String) = log(level, "    INC - $s")
}
