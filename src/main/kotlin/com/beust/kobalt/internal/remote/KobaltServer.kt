package com.beust.kobalt.internal.remote

import com.beust.kobalt.Args
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.SocketException

interface ICommandSender {
    fun sendData(content: String)
}

interface ICommand {
    val name: String
    fun run(sender: ICommandSender, received: JsonObject)
}

class CommandData(val commandName: String, val data: String)

@Singleton
public class KobaltServer @Inject constructor(val args: Args) : Runnable, ICommandSender {
    var outgoing: PrintWriter? = null
    val pending = arrayListOf<String>()

    private val COMMAND_CLASSES = listOf(GetDependenciesCommand::class.java, PingCommand::class.java)
    private val COMMANDS = mapOf(*(COMMAND_CLASSES.map { it ->
            Kobalt.INJECTOR.getInstance(it).let { Pair(it.name, it) }
        }.toTypedArray()))

    override fun run() {
        val portNumber = args.port

        log1("Listening to port $portNumber")
        var quit = false
        val serverSocket = ServerSocket(portNumber)
        while (! quit) {
            val clientSocket = serverSocket.accept()
            outgoing = PrintWriter(clientSocket.outputStream, true)
            if (pending.size() > 0) {
                log1("Emptying the queue, size $pending.size()")
                synchronized(pending) {
                    pending.forEach { sendData(it) }
                    pending.clear()
                }
            }
            val ins = BufferedReader(InputStreamReader(clientSocket.inputStream))
            try {
                var line = ins.readLine()
                while (!quit && line != null) {
                    log1("Received from client $line")
                    val jo = JsonParser().parse(line) as JsonObject
                    if ("quit" == jo.get("name").asString) {
                        log1("Quitting")
                        quit = true
                    } else {
                        runCommand(jo)

                        // Done, send a quit to the client
                        sendData("{ \"name\": \"quit\" }")

                        line = ins.readLine()
                    }
                }
            } catch(ex: SocketException) {
                log1("Client disconnected, resetting")
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

    override fun sendData(content: String) {
        if (outgoing != null) {
            outgoing!!.println(content)
        } else {
            log1("Queuing $content")
            synchronized(pending) {
                pending.add(content)
            }
        }
    }

    private fun log1(s: String) {
        log(1, "[KobaltServer] $s")
    }

    private fun log2(s: String) {
        log(2, "[KobaltServer] $s")
    }
}


