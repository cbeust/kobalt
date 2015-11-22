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

    /**
     * Some commands fail but return 0, so the only way to find out if they failed is to look
     * at the error stream. However, some commands succeed but output text on the error stream.
     * This field is used to specify how errors are caught.
     */
    var useErrorStreamAsErrorIndicator = true

    fun useErrorStreamAsErrorIndicator(f: Boolean) : RunCommand {
        useErrorStreamAsErrorIndicator = f
        return this
    }

    fun run(args: List<String>,
            errorCallback: Function1<List<String>, Unit> = defaultError,
            successCallback: Function1<List<String>, Unit> = defaultSuccess) : Int {
        val allArgs = arrayListOf<String>()
        allArgs.add(command)
        allArgs.addAll(args)

        val pb = ProcessBuilder(allArgs)
        pb.directory(directory)
        log(2, "Running command in directory ${directory.absolutePath}" +
            "\n  " + allArgs.joinToString(" ").replace("\\", "/"))
        val process = pb.start()
        pb.environment().let { pbEnv ->
            env.forEach {
                pbEnv.put(it.key, it.value)
            }
        }
        val callSucceeded = process.waitFor(30, TimeUnit.SECONDS)
        val hasErrorStream = process.errorStream.available() > 0
        var hasErrors = ! callSucceeded
        if (useErrorStreamAsErrorIndicator && ! hasErrors) {
            hasErrors = hasErrors && hasErrorStream
        }

        if (! hasErrors) {
            successCallback(fromStream(process.inputStream))
        } else {
            val stream = if (hasErrorStream) process.errorStream
                else if (process.inputStream.available() > 0) process.inputStream
                else null
            val errorString =
                if (stream != null) fromStream(stream).joinToString("\n")
                else "<no output>"
            errorCallback(listOf("$command failed") + errorString)
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
