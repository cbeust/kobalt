package com.beust.kobalt.internal.remote

import com.beust.kobalt.api.Project
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * A simple command that returns its own content.
 * Payload:
 * {
 *   "name" : "ping"
 * }
 *
 */
class PingCommand() : ICommand {
    override val name = "ping"

    override fun run(sender: ICommandSender, received: JsonObject, initCallback: (String) -> List<Project>) {
        sender.sendData(toCommandData(Gson().toJson(PingData(received.toString()))))
    }

    class PingData(val received: String)
}

