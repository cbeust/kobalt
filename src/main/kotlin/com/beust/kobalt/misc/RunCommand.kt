package com.beust.kobalt.misc

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

public class RunCommand(val command: String) {
    val defaultSuccess = { output: List<String> -> log(1, "Success:\n " + output.join("\n"))}
    val defaultError = { output: List<String> -> log(1, "Error:\n " + output.join("\n"))}

    var directory = File(".")

    fun run(args: List<String>, error: Function1<List<String>, Unit>? = defaultError,
            success: Function1<List<String>, Unit>? = defaultSuccess) : Int {
        val allArgs = arrayListOf<String>()
        allArgs.add(command)
        allArgs.addAll(args)

        val pb = ProcessBuilder(allArgs)
        pb.directory(directory)
        log(1, "Running command: " + allArgs.join(" "))
        val process = pb.start()
        pb.environment().put("ANDROID_HOME", "/Users/beust/android/adt-bundle-mac-x86_64-20140702/sdk")
        val errorCode = process.waitFor()
        if (errorCode != 0 && error != null) {
            error(fromStream(process.errorStream))
        } else if (errorCode == 0 && success != null){
            success(fromStream(process.inputStream))
        }
        return errorCode

    }

    private fun fromStream(ins: InputStream) : List<String> {
        val result = arrayListOf<String>()
        val br = BufferedReader(InputStreamReader(ins))
        var line = br.readLine()
        while (line != null) {
            result.add(line)
            line = br.readLine()
        }
        return result
    }
}
