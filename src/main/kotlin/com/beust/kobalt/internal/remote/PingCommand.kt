package com.beust.kobalt.internal.remote

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
    override fun run(sender: ICommandSender, received: JsonObject) =
            sender.sendData("{ \"response\" : \"${received.toString()}\"" + " }")
}

