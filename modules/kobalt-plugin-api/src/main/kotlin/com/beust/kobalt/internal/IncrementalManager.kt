package com.beust.kobalt.internal

import com.beust.kobalt.misc.KFiles
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
        val BUILD_INFO_FILE = KFiles.joinDir(KFiles.KOBALT_DOT_DIR, "build-info.json")
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
}
