package com.beust.kobalt.misc

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class Io(val dryRun: Boolean = false) {
    fun mkdirs(dir: String) {
        log("mkdirs $dir")
        if (! dryRun) {
            File(dir).mkdirs()
        }
    }

    fun rm(path: String) {
        log("rm $path")

        if (! dryRun) {
            File(path).deleteRecursively()
        }
    }

    fun moveFile(from: File, toDir: File) {
        log("mv $from $toDir")
        if (! dryRun) {
            require(from.exists(), { -> "$from should exist" })
            require(from.isFile, { -> "$from should be a file" })
            require(toDir.isDirectory, { -> "$toDir should be a file"})

            val toFile = File(toDir, from.name)
            Files.move(Paths.get(from.absolutePath), Paths.get(toFile.absolutePath), StandardCopyOption.ATOMIC_MOVE)
        }
    }

    fun copyDirectory(from: File, toDir: File) {
        log("cp -r $from $toDir")

        if (! dryRun) {
            KFiles.copyRecursively(from, toDir)
            require(from.exists(), { -> "$from should exist" })
            require(from.isDirectory, { -> println("$from should be a directory")})
            require(toDir.isDirectory, { -> println("$toDir should be a file")})

        }
    }

    fun rmDir(dir: File) {
        log("rm -rf $dir")

        if (! dryRun) {
            require(dir.isDirectory,  { -> println("$dir should be a directory")})
            dir.deleteRecursively()
        }
    }

    fun mkdir(dir: File) {
        log("rm -rf $dir")
        if (! dryRun) {
            dir.mkdirs()
        }
    }

    private fun log(s: String) {
        log(1, "[Io] $s")
    }

}
