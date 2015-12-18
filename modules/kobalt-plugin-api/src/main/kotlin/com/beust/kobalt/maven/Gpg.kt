package com.beust.kobalt.maven

import com.beust.kobalt.OperatingSystem
import com.beust.kobalt.misc.error
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.inject.Singleton
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

@Singleton
public class Gpg {
    val COMMANDS = listOf("gpg", "gpg2")

     fun findGpgCommand() : String? {
        val path = System.getenv("PATH")
        if (path != null) {
            path.split(File.pathSeparator).forEach { dir ->
                COMMANDS.map {
                    File(dir, if (OperatingSystem.current().isWindows()) it + ".exe" else it)
                }.firstOrNull {
                    it.exists()
                }?.let {
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
                val ascFile = File(file.absolutePath + ".asc")
                ascFile.delete()
                val allArgs = arrayListOf<String>()
                allArgs.add(gpg)
                allArgs.add("-ab")
                allArgs.add(file.absolutePath)

                val pb = ProcessBuilder(allArgs)
                pb.directory(directory)
                log(2, "Signing file: " + allArgs.joinToString(" "))
                val process = pb.start()

                val br = BufferedReader(InputStreamReader(process.errorStream))
                val errorCode = process.waitFor()
                if (errorCode != 0) {
                    var line = br.readLine()
                    while (line != null) {
                        error(line)
                        line = br.readLine()
                    }
                    warn("Couldn't sign file $file")
                } else {
                    result.add(ascFile)
                }
            }

            return result
        } else {
            warn("Couldn't find the gpg command, make sure it is on your PATH")
            warn("Signing of artifacts with PGP (.asc) disabled")
            return arrayListOf()
        }
    }
}
