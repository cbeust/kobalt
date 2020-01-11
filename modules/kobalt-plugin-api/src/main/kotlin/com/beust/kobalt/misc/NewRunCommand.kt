package com.beust.kobalt.misc

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RunCommandInfo {
    lateinit var command: String
    var args : List<String> = arrayListOf()
    var directory : File = File("")
    var env : Map<String, String> = hashMapOf()

    /**
     * Some commands fail but return 0, so the only way to find out if they failed is to look
     * at the error stream. However, some commands succeed but output text on the error stream.
     * This field is used to specify how errors are caught.
     */
    var useErrorStreamAsErrorIndicator : Boolean = true
    var useInputStreamAsErrorIndicator : Boolean = false
    var ignoreExitValue : Boolean = false

    var errorCallback: Function1<List<String>, Unit> = NewRunCommand.DEFAULT_ERROR
    var successCallback: Function1<List<String>, Unit> = NewRunCommand.DEFAULT_SUCCESS

    var isSuccess: (Boolean, List<String>, List<String>) -> Boolean = {
        isSuccess: Boolean,
                input: List<String>,
                error: List<String> ->
        var hasErrors = ! isSuccess
        if (useErrorStreamAsErrorIndicator && ! hasErrors) {
            hasErrors = hasErrors || error.size > 0
        }
        if (useInputStreamAsErrorIndicator && ! hasErrors) {
            hasErrors = hasErrors || input.size > 0
        }

        ! hasErrors
    }

    var containsErrors: ((List<String>) -> Boolean)? = null
}

fun runCommand(init: RunCommandInfo.() -> Unit) = NewRunCommand(RunCommandInfo().apply { init() }).invoke()

open class NewRunCommand(val info: RunCommandInfo) {

    companion object {
        val DEFAULT_SUCCESS = { output: List<String> -> }
        //    val DEFAULT_SUCCESS_VERBOSE = { output: List<String> -> kobaltLog(2, "Success:\n " + output.joinToString
        // ("\n"))}
        //        val defaultSuccess = DEFAULT_SUCCESS
        val DEFAULT_ERROR = { output: List<String> ->
            kobaltError(output.joinToString("\n       "))
        }
    }

    //    fun useErrorStreamAsErrorIndicator(f: Boolean) : RunCommand {
    //        useErrorStreamAsErrorIndicator = f
    //        return this
    //    }

    fun invoke() : Int {
        val allArgs = arrayListOf<String>()
        allArgs.add(info.command)
        allArgs.addAll(info.args)

        val pb = ProcessBuilder(allArgs)
        pb.directory(info.directory)
        kobaltLog(2, "Running command in directory ${info.directory.absolutePath}" +
                "\n  " + allArgs.joinToString(" ").replace("\\", "/"))
        pb.environment().let { pbEnv ->
            info.env.forEach {
                pbEnv.put(it.key, it.value)
            }
        }

        val process = pb.start()

        // Run the command and collect the return code and streams
        val processFinished = process.waitFor(240, TimeUnit.SECONDS)
        if (!processFinished)
            kobaltError("process timed out!")

        val processExitedOk = process.exitValue() == 0
        if (!processExitedOk)
            kobaltError("process returned non-zero code!")

        val input =
                if (process.inputStream.available() > 0) fromStream(process.inputStream)
                else listOf()
        val error =
                if (process.errorStream.available() > 0) fromStream(process.errorStream)
                else listOf()

        kobaltLog(3, "info contains errors: " + (info.containsErrors != null))

        // Check to see if the command succeeded
        val isSuccess =
                if (info.containsErrors != null) ! info.containsErrors!!(error)
                else isSuccess(processFinished && if (info.ignoreExitValue) true else processExitedOk, input, error)

        if (isSuccess) {
            if (!info.useErrorStreamAsErrorIndicator) {
                info.successCallback(error + input)
            } else {
                info.successCallback(input)
            }
        } else {
            info.errorCallback(error + input)
        }

        return if (isSuccess) 0 else 1
    }

    /**
     * Subclasses can override this method to do their own error handling, since commands can
     * have various ways to signal errors.
     */
    open protected fun isSuccess(isSuccess: Boolean, input: List<String>, error: List<String>) : Boolean {
        var hasErrors: Boolean = ! isSuccess
        if (info.useErrorStreamAsErrorIndicator && ! hasErrors) {
            hasErrors = hasErrors || error.isNotEmpty()
        }
        if (info.useInputStreamAsErrorIndicator && ! hasErrors) {
            hasErrors = hasErrors || input.isNotEmpty()
        }

        return ! hasErrors
    }

    /**
     * Turn the given InputStream into a list of strings.
     */
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
