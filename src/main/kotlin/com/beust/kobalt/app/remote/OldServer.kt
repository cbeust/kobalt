package com.beust.kobalt.app.remote

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.remote.CommandData
import com.beust.kobalt.internal.remote.ICommandSender
import com.beust.kobalt.internal.remote.PingCommand
import com.beust.kobalt.misc.kobaltLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.SocketException

@Deprecated(message = "Replaced by Websocket server, to be deleted")
class OldServer(val initCallback: (String) -> List<Project>, val cleanUpCallback: () -> Unit)
        : KobaltServer.IServer, ICommandSender {
    val pending = arrayListOf<CommandData>()

    override fun run(port: Int) {
        kobaltLog(1, "Listening to port $port")
        var quit = false
        serverInfo = ServerInfo(port)
        while (!quit) {
            if (pending.size > 0) {
                kobaltLog(1, "Emptying the queue, size $pending.size()")
                synchronized(pending) {
                    pending.forEach { sendData(it) }
                    pending.clear()
                }
            }
            var commandName: String? = null
            try {
                var line = serverInfo.reader.readLine()
                while (!quit && line != null) {
                    kobaltLog(1, "Received from client $line")
                    val jo = JsonParser().parse(line) as JsonObject
                    commandName = jo.get("name").asString
                    if ("quit" == commandName) {
                        kobaltLog(1, "Quitting")
                        quit = true
                    } else {
                        runCommand(jo, initCallback)

                        // Done, send a quit to the client
                        sendData(CommandData("quit", ""))

                        // Clean up all the plug-in actors
                        cleanUpCallback()
                        line = serverInfo.reader.readLine()
                    }
                }
                if (line == null) {
                    kobaltLog(1, "Received null line, resetting the server")
                    serverInfo.reset()
                }
            } catch(ex: SocketException) {
                kobaltLog(1, "Client disconnected, resetting")
                serverInfo.reset()
            } catch(ex: Throwable) {
                ex.printStackTrace()
                if (commandName != null) {
                    sendData(CommandData(commandName, null, ex.message))
                }
                kobaltLog(1, "Command failed: ${ex.message}")
            }
        }
    }

    private val COMMAND_CLASSES = listOf(GetDependenciesCommand::class.java, PingCommand::class.java)
    private val COMMANDS = COMMAND_CLASSES.map {
        Kobalt.INJECTOR.getInstance(it).let { Pair(it.name, it) }
    }.toMap()

    private fun runCommand(jo: JsonObject, initCallback: (String) -> List<Project>) {
        val command = jo.get("name").asString
        if (command != null) {
            (COMMANDS[command] ?: COMMANDS["ping"])!!.run(this, jo, initCallback)
        } else {
            error("Did not find a name in command: $jo")
        }
    }

    lateinit var serverInfo: ServerInfo

    class ServerInfo(val port: Int) {
        lateinit var reader: BufferedReader
        lateinit var writer: PrintWriter
        var serverSocket : ServerSocket? = null

        init {
            reset()
        }

        fun reset() {
            if (serverSocket != null) {
                serverSocket!!.close()
            }
            serverSocket = ServerSocket(port)
            var clientSocket = serverSocket!!.accept()
            reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
            writer = PrintWriter(clientSocket.outputStream, true)
        }
    }

    override fun sendData(commandData: CommandData) {
        val content = Gson().toJson(commandData)
        if (serverInfo.writer != null) {
            serverInfo.writer!!.println(content)
        } else {
            kobaltLog(1, "Queuing $content")
            synchronized(pending) {
                pending.add(commandData)
            }
        }
    }

}
