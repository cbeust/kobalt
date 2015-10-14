package com.beust.kobalt.maven

import com.beust.kobalt.misc.log
import com.google.inject.Singleton
import java.io.File

@Singleton
public class Gpg {
    val COMMANDS = listOf("gpg", "gpg2")

     fun findGpgCommand() : String? {
        val path = System.getenv("PATH")
        if (path != null) {
            path.split(File.pathSeparator).forEach { dir ->
                COMMANDS.map { File(dir, it) }.firstOrNull { it.exists() }?.let {
                    return it.absolutePath
                }
            }
        }
        return null
    }

    /**
     * @return the .asc files
     */
    fun runGpg(files: List<File>) : List<File> {
        val result = arrayListOf<File>()
        val gpg = findGpgCommand()
        if (gpg != null) {
            val directory = files.get(0).parentFile.absoluteFile
            files.forEach { file ->
                with(File(directory, file.absolutePath + ".asc")) {
                    delete()
                    result.add(this)
                }
                val allArgs = arrayListOf<String>()
                allArgs.add(gpg)
                allArgs.add("-ab")
                allArgs.add(file.absolutePath)

                val pb = ProcessBuilder(allArgs)
                pb.directory(directory)
                pb.inheritIO()
                log(2, "Signing file: " + allArgs.join(" "))
                val process = pb.start()
                val errorCode = process.waitFor()
                if (errorCode != 0) {
                    throw KobaltException("Couldn't sign file $file")
                }
            }

            return files.map { File(it.absolutePath + ".asc") }
        } else {
            throw KobaltException("Couldn't find the command, is it installed and in your PATH?")
        }
    }
}
