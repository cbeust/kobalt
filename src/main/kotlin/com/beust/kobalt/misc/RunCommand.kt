package com.beust.kobalt.misc

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

open class RunCommand(val command: String) {
    val DEFAULT_SUCCESS = { output: List<String> -> }
//    val DEFAULT_SUCCESS_VERBOSE = { output: List<String> -> log(2, "Success:\n " + output.joinToString("\n"))}
    val defaultSuccess = DEFAULT_SUCCESS
    val defaultError = {
        output: List<String> -> error("Error:\n " + output.joinToString("\n"))
    }

    var directory = File(".")
    var env = hashMapOf<String, String>()

    fun run(args: List<String>,
            errorCallback: Function1<List<String>, Unit> = defaultError,
            successCallback: Function1<List<String>, Unit> = defaultSuccess) : Int {
        val allArgs = arrayListOf<String>()
        allArgs.add(command)
        allArgs.addAll(args)

        val pb = ProcessBuilder(allArgs)
        pb.directory(directory)
        log(2, "Running command: " + allArgs.joinToString(" ") + "\n      Current directory: $directory")
        val process = pb.start()
        pb.environment().let { pbEnv ->
            env.forEach {
                pbEnv.put(it.key, it.value)
            }
        }
        val callSucceeded = process.waitFor(30, TimeUnit.SECONDS)
//        val callSucceeded = if (passed == 0) true else false
        if (callSucceeded) {
            successCallback(fromStream(process.inputStream))
        } else {
            errorCallback(listOf("$command failed") + fromStream(process.errorStream))
        }
        return if (callSucceeded) 0 else 1

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
