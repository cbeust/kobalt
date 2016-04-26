package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.remote.ICommand
import com.beust.kobalt.internal.remote.ICommandSender
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject

/**
 * This command returns the list of dependencies for the given buildFile.
 * Payload:
 * { "name" : "getDependencies", "buildFile": "/Users/beust/kotlin/kobalt/kobalt/src/Build.kt" }
 * The response is a GetDependenciesData.
 */
class GetDependenciesCommand @Inject constructor(val args: Args, val dependencyData: DependencyData) : ICommand {

    override val name = "getDependencies"

    override fun run(sender: ICommandSender, received: JsonObject, initCallback: (String) -> List<Project>) {
        val buildFile = received.get("buildFile").asString
        val projects = initCallback(buildFile)
        val dd = dependencyData.dependenciesDataFor(buildFile, args)
        val data = toCommandData(Gson().toJson(dd), dd.errorMessage)
        sender.sendData(data)
    }
}