package com.beust.kobalt.plugin.android

import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.runCommand
import java.io.File

open class AndroidCommand(project: Project, val androidHome: String, val command: String,
        val directory: File = File(project.directory),
        val useErrorStreamAsErrorIndicator : Boolean = true,
        val args: List<String>)
//        : RunCommand(command, directory = cwd, args = args
//        ,
//        successCallback = { output ->
//            log(1, "$command succeeded:")
//            output.forEach {
//                log(1, "  $it")
//            }
//        }
 {


//    val SUCCESS_CALLBACK : (List<String>) -> Unit = { output ->
//        log(1, "$command succeeded:")
//        output.forEach {
//            log(1, "  $it")
//        }
//    }
//
//    val ERROR_CALLBACK : (List<String>) -> Unit = { output ->
//        with(StringBuilder()) {
//            append("Error running $command:")
//            output.forEach {
//                append("  $it")
//            }
//            error(this.toString())
//        }
//    }nComman

    open fun call(theseArgs: List<String>) : Int {
        val rc = runCommand {
            args = theseArgs
            useErrorStreamAsErrorIndicator = useErrorStreamAsErrorIndicator
            directory = directory
            env = hashMapOf("ANDROID_HOME" to androidHome)
            successCallback = { output ->
                log(1, "$command succeeded:")
                output.forEach {
                    log(1, "  $it")
                }
            }
            errorCallback = { output ->
                with(StringBuilder()) {
                    append("Error running $command:")
                    output.forEach {
                        append("  $it")
                    }
                    error(this.toString())
                }
            }
        }
        return rc
    }
}


