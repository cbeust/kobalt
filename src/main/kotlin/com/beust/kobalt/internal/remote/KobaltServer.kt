package com.beust.kobalt.internal.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.SocketException

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
class CommandData(val name: String, val data: String)

@Singleton
public class KobaltServer @Inject constructor(val args: Args) : Runnable, ICommandSender {
    var outgoing: PrintWriter? = null
    val pending = arrayListOf<CommandData>()

    private val COMMAND_CLASSES = listOf(GetDependenciesCommand::class.java, PingCommand::class.java)
    private val COMMANDS = COMMAND_CLASSES.map {
            Kobalt.INJECTOR.getInstance(it).let { Pair(it.name, it) }
        }.toMap()

    override fun run() {
        val portNumber = args.port

        log(1, "Listening to port $portNumber")
        var quit = false
        val serverSocket = ServerSocket(portNumber)
        while (! quit) {
            val clientSocket = serverSocket.accept()
            outgoing = PrintWriter(clientSocket.outputStream, true)
            if (pending.size() > 0) {
                log(1, "Emptying the queue, size $pending.size()")
                synchronized(pending) {
                    pending.forEach { sendData(it) }
                    pending.clear()
                }
            }
            val ins = BufferedReader(InputStreamReader(clientSocket.inputStream))
            try {
                var line = ins.readLine()
                while (!quit && line != null) {
                    log(1, "Received from client $line")
                    val jo = JsonParser().parse(line) as JsonObject
                    if ("quit" == jo.get("name").asString) {
                        log(1, "Quitting")
                        quit = true
                    } else {
                        runCommand(jo)

                        // Done, send a quit to the client
                        sendData(CommandData("quit", ""))

                        line = ins.readLine()
                    }
                }
            } catch(ex: SocketException) {
                log(1, "Client disconnected, resetting")
            }
        }
    }

    private fun runCommand(jo: JsonObject) {
        val command = jo.get("name").asString
        if (command != null) {
            COMMANDS.getOrElse(command, { COMMANDS.get("ping") })!!.run(this, jo)
        } else {
            error("Did not find a name in command: $jo")
        }
    }

    override fun sendData(commandData: CommandData) {
        val content = Gson().toJson(commandData)
        if (outgoing != null) {
            outgoing!!.println(content)
        } else {
            log(1, "Queuing $content")
            synchronized(pending) {
                pending.add(commandData)
            }
        }
    }
}


