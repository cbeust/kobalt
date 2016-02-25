package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.internal.remote.CommandData
import com.beust.kobalt.internal.remote.ICommandSender
import com.beust.kobalt.internal.remote.PingCommand
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Singleton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.SocketException
import javax.inject.Inject

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
        var clientSocket = serverSocket.accept()
        while (!quit) {
            outgoing = PrintWriter(clientSocket.outputStream, true)
            if (pending.size > 0) {
                log(1, "Emptying the queue, size $pending.size()")
                synchronized(pending) {
                    pending.forEach { sendData(it) }
                    pending.clear()
                }
            }
            val ins = BufferedReader(InputStreamReader(clientSocket.inputStream))
            var commandName: String? = null
            try {
                var line = ins.readLine()
                while (!quit && line != null) {
                    log(1, "Received from client $line")
                    val jo = JsonParser().parse(line) as JsonObject
                    commandName = jo.get("name").asString
                    if ("quit" == commandName) {
                        log(1, "Quitting")
                        quit = true
                    } else {
                        runCommand(jo)

                        // Done, send a quit to the client
                        sendData(CommandData("quit", ""))

                        line = ins.readLine()
                    }
                }
                if (line == null) {
                    quit = true
                }
            } catch(ex: SocketException) {
                log(1, "Client disconnected, resetting")
                clientSocket = serverSocket.accept()
            } catch(ex: Throwable) {
                ex.printStackTrace()
                sendData(CommandData(commandName!!, null, ex.message))
                log(1, "Command failed: ${ex.message}")
            }
        }
    }

    private fun runCommand(jo: JsonObject) {
        val command = jo.get("name").asString
        if (command != null) {
            (COMMANDS[command] ?: COMMANDS["ping"])!!.run(this, jo)
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