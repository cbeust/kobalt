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
    val DEFAULT_ERROR = {
        output: List<String> ->
        error(output.joinToString("\n       "))
    }

    var directory = File(".")
    var env = hashMapOf<String, String>()

    /**
     * Some commands fail but return 0, so the only way to find out if they failed is to look
     * at the error stream. However, some commands succeed but output text on the error stream.
     * This field is used to specify how errors are caught.
     */
    var useErrorStreamAsErrorIndicator = true
    var useInputStreamAsErrorIndicator = false

    fun useErrorStreamAsErrorIndicator(f: Boolean): RunCommand {
        useErrorStreamAsErrorIndicator = f
        return this
    }

    open fun run(args: List<String>,
                 errorCallback: Function1<List<String>, Unit> = DEFAULT_ERROR,
                 successCallback: Function1<List<String>, Unit> = defaultSuccess): Int {
        val allArgs = arrayListOf<String>()
        allArgs.add(command)
        allArgs.addAll(args)

        val pb = ProcessBuilder(allArgs)
        pb.directory(directory)
        log(2, "Running command in directory ${directory.absolutePath}" +
                "\n  " + allArgs.joinToString(" "))
        val process = pb.start()
        pb.environment().let { pbEnv ->
            env.forEach { it ->
                pbEnv.put(it.key, it.value)
            }
        }
        val callSucceeded = process.waitFor(30, TimeUnit.SECONDS)
        val input = if (process.inputStream.available() > 0) fromStream(process.inputStream) else emptyList()
        val error = if (process.errorStream.available() > 0) fromStream(process.errorStream) else emptyList()
        val isSuccess = isSuccess(callSucceeded, input, error)

        if (isSuccess) {
            successCallback(fromStream(process.inputStream))
        } else {
            errorCallback(error + input)
        }

        return if (isSuccess) 0 else 1
    }

    open protected fun isSuccess(callSucceeded: Boolean, input: List<String>, error: List<String>): Boolean {
        var hasErrors = !callSucceeded
        if (useErrorStreamAsErrorIndicator && !hasErrors) {
            hasErrors = hasErrors || error.size > 0
        }
        if (useInputStreamAsErrorIndicator && !hasErrors) {
            hasErrors = hasErrors || input.size > 0
        }

        return !hasErrors
    }

    private fun fromStream(ins: InputStream): List<String> {
        val result = arrayListOf<String>()
        val br = BufferedReader(InputStreamReader(ins))
        var line = br.readLine()

        while (line != null) {
            result.add(line)
            line = br.readLine()
        }
        return result

        //        val result = CharStreams.toString(InputStreamReader(ins, Charset.defaultCharset()))
        //        return result.split("\n")
    }
}
