package com.beust.kobalt.internal.remote

import com.google.gson.JsonObject

class PingCommand() : ICommand {
    override val name = "ping"
    override fun run(sender: ICommandSender, received: JsonObject) =
            sender.sendData("{ \"response\" : \"${received.toString()}\"" + " }")
}

