package com.beust.kobalt.misc

import java.io.File
import java.nio.file.*

class Io(val dryRun: Boolean = false) {
    fun mkdirs(dir: String) {
        kobaltLog("mkdirs $dir")
        if (! dryRun) {
            File(dir).mkdirs()
        }
    }

    fun rm(path: String) {
        kobaltLog("rm $path")

        if (! dryRun) {
            File(path).deleteRecursively()
        }
    }

    fun moveFile(from: File, toDir: File) {
        kobaltLog("mv $from $toDir")
        if (! dryRun) {
            require(from.exists(), { -> "$from should exist" })
            require(from.isFile, { -> "$from should be a file" })
            require(toDir.isDirectory, { -> "$toDir should be a file"})

            val toFile = File(toDir, from.name)
            Files.move(Paths.get(from.absolutePath), Paths.get(toFile.absolutePath), StandardCopyOption.ATOMIC_MOVE)
        }
    }

    fun rename(from: File, to: File) {
        kobaltLog("rename $from $to")
        moveFile(from, to.parentFile)
        if (from.name != to.name) {
            File(to, from.name).renameTo(to)
        }
    }

    fun copyDirectory(from: File, toDir: File) {
        kobaltLog("cp -r $from $toDir")

        if (! dryRun) {
            KFiles.copyRecursively(from, toDir)
            require(from.exists(), { -> "$from should exist" })
            require(from.isDirectory, { -> kobaltLog(1, "$from should be a directory")})
            require(toDir.isDirectory, { -> kobaltLog(1, "$toDir should be a file")})

        }
    }

    fun rmDir(dir: File, keep: (File) -> Boolean = { t -> false }) = rmDir(dir, keep, "  ")

    private fun rmDir(dir: File, keep: (File) -> Boolean, indent : String) {
        kobaltLog("rm -rf $dir")

        require(dir.isDirectory,  { -> kobaltLog(1, "$dir should be a directory")})

        dir.listFiles({ p0 -> ! keep(p0!!) }).forEach {
            if (it.isDirectory) {
                rmDir(it, keep, indent + "  ")
                it.deleteRecursively()
            }
            else {
                kobaltLog(indent + "rm $it")
                if (! dryRun) it.delete()
            }
        }
    }

    fun mkdir(dir: File) {
        kobaltLog("mkdir $dir")
        if (! dryRun) {
            dir.mkdirs()
        }
    }

    private fun kobaltLog(s: String) {
        kobaltLog(1, "[Io] $s")
    }

}
