package com.beust.kobalt.internal.remote

import com.beust.kobalt.misc.log
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

    override fun run(sender: ICommandSender, received: JsonObject) {
        val commandData = toCommandDataJson(Gson().toJson(PingData(received.toString())))
        val result = Gson().toJson(commandData)
        log(1, "ping returning: $result")
        sender.sendData(result)
    }

    class PingData(val received: String)
}

