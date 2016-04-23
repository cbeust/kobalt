package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.remote.CommandData
import com.beust.kobalt.internal.remote.ICommandSender
import com.beust.kobalt.internal.remote.PingCommand
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Singleton
import java.io.*
import java.net.ServerSocket
import java.net.SocketException
import java.util.*
import javax.inject.Inject

@Singleton
class KobaltServer @Inject constructor(val args: Args, val pluginInfo: PluginInfo) : Runnable, ICommandSender {
//    var outgoing: PrintWriter? = null
    val pending = arrayListOf<CommandData>()

    private val COMMAND_CLASSES = listOf(GetDependenciesCommand::class.java, PingCommand::class.java)
    private val COMMANDS = COMMAND_CLASSES.map {
            Kobalt.INJECTOR.getInstance(it).let { Pair(it.name, it) }
        }.toMap()

    override fun run() {
        try {
            if (createServerFile(args.port)) {
                privateRun()
            }
        } catch(ex: Exception) {
            ex.printStackTrace()
        } finally {
            deleteServerFile()
        }
    }

    val SERVER_FILE = KFiles.joinDir(homeDir(KFiles.KOBALT_DOT_DIR, "kobaltServer.properties"))
    val KEY_PORT = "port"

    private fun createServerFile(port: Int) : Boolean {
        if (File(SERVER_FILE).exists()) {
            log(1, "Server file $SERVER_FILE already exists, is another server running?")
            return false
        } else {
            Properties().apply {
                put(KEY_PORT, port.toString())
            }.store(FileWriter(SERVER_FILE), "")
            log(2, "KobaltServer created $SERVER_FILE")
            return true
        }
    }

    private fun deleteServerFile() {
        log(1, "KobaltServer deleting $SERVER_FILE")
        File(SERVER_FILE).delete()
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

    private fun privateRun() {
        val portNumber = args.port

        log(1, "Listening to port $portNumber")
        var quit = false
        serverInfo = ServerInfo(portNumber)
        while (!quit) {
            if (pending.size > 0) {
                log(1, "Emptying the queue, size $pending.size()")
                synchronized(pending) {
                    pending.forEach { sendData(it) }
                    pending.clear()
                }
            }
            var commandName: String? = null
            try {
                var line = serverInfo.reader.readLine()
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

                        line = serverInfo.reader.readLine()
                    }
                }
                if (line == null) {
                    serverInfo.reset()
                }
            } catch(ex: SocketException) {
                log(1, "Client disconnected, resetting")
                serverInfo.reset()
            } catch(ex: Throwable) {
                ex.printStackTrace()
                if (commandName != null) {
                    sendData(CommandData(commandName, null, ex.message))
                }
                log(1, "Command failed: ${ex.message}")
            }

            pluginInfo.shutdown()
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
        if (serverInfo.writer != null) {
            serverInfo.writer!!.println(content)
        } else {
            log(1, "Queuing $content")
            synchronized(pending) {
                pending.add(commandData)
            }
        }
    }
}