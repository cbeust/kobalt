package com.beust.kobalt.internal.remote

import com.google.gson.JsonObject

/**
 * All commands implement this interface.
 */
interface ICommand {
    /**
     * The name of this command.
     */
    val name: String

    /**
     * Run this command based on the information received from the client. When done, use
     * the sender object to send back a response.
     */
    fun run(sender: ICommandSender, received: JsonObject)

    fun toCommandData(data: String) = CommandData(name, data)
}

/**
 * Passed to a command in its `run` method so it can send information back to the caller.
 * @param The string content that will be sent in the "data" field.
 */
interface ICommandSender {
    fun sendData(commandData: CommandData)
}

/**
 * The JSON payload that commands exchange follow the following pattern:
 * {
 *   name: "nameOfTheCommand"
 *   data: a JSON string containing the payload itself
 * }
 * This allows commands to be tested for their name first, after which each command can
 * decode its own specific payload by parsing the JSON in the "data" field and mapping
 * it into a Kotlin *Data class. The downside of this approach is a double parsing,
 * but since the data part is parsed as a string first, this is probably not a huge deal.
 */
class CommandData(val name: String, val data: String?, val error: String? = null)


