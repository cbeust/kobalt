package com.beust.kobalt.plugin.android

import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.RunCommand
import com.beust.kobalt.misc.log
import java.io.File

open class AndroidCommand(project: Project, androidHome: String, command: String, cwd: File = File(project.directory))
: RunCommand(command) {
    init {
        env.put("ANDROID_HOME", androidHome)
        directory = cwd
    }

    open fun call(args: List<String>) = run(args,
            successCallback = { output ->
                log(1, "$command succeeded:")
                output.forEach {
                    log(1, "  $it")
                }
            },
            errorCallback = { output ->
                with(StringBuilder()) {
                    append("Error running $command:")
                    output.forEach {
                        append("  $it")
                    }
                    error(this.toString())
                }
            })
}


