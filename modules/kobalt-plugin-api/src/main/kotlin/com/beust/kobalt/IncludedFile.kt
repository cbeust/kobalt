package com.beust.kobalt

import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.toString
import java.io.File
import java.nio.file.Paths

class IncludedFile(val fromOriginal: From, val toOriginal: To, val specs: List<IFileSpec>,
        val expandJarFiles: Boolean = false) {
    constructor(specs: List<IFileSpec>, expandJarFiles: Boolean = false) : this(From(""), To(""), specs, expandJarFiles)
    fun from(s: String) = File(if (fromOriginal.isCurrentDir()) s else KFiles.joinDir(from, s))
    val from: String get() = fromOriginal.path.replace("\\", "/")
    fun to(s: String) = File(if (toOriginal.isCurrentDir()) s else KFiles.joinDir(to, s))
    val to: String get() = toOriginal.path.replace("\\", "/")
    override fun toString() = toString("IncludedFile",
            "files - ", specs.map { it.toString() },
            "from", from,
            "to", to)

    fun allFromFiles(directory: String? = null): List<File> {
        val result = arrayListOf<File>()
        specs.forEach { spec ->
//            val fullDir = if (directory == null) from else KFiles.joinDir(directory, from)
            spec.toFiles(directory, from).forEach { source ->
                result.add(if (source.isAbsolute) source else File(source.path))
            }
        }
        return result.map { Paths.get(it.path).normalize().toFile()}
    }
}

open class Direction(open val p: String) {
    override fun toString() = path
    fun isCurrentDir() = path == "./"

    val path: String get() =
        if (p.isEmpty()) "./"
        else if (p.startsWith("/") || p.endsWith("/")) p
        else p + "/"
}

class From(override val p: String) : Direction(p)

class To(override val p: String) : Direction(p)
