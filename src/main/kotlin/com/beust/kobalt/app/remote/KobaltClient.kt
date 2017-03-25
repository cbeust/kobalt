package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.MainModule
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.GraphUtil
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.maven.aether.Exceptions
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.warn
import com.google.gson.Gson
import com.google.inject.Guice
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ws.WebSocket
import okhttp3.ws.WebSocketCall
import okhttp3.ws.WebSocketListener
import okio.Buffer
import java.io.IOException

fun main(argv: Array<String>) {
    Kobalt.INJECTOR = Guice.createInjector(MainModule(Args(), KobaltSettings.readSettingsXml()))
    KobaltClient().run()
}

class KobaltClient : Runnable {
    override fun run() {
        val client = OkHttpClient()
        val port = KobaltServer.port ?: 1240
        val url = "ws://localhost:$port/v1/getDependencyGraph"
        val buildFile = KFiles.fixSlashes(homeDir("kotlin/kobalt/kobalt/src/Build.kt"))
        val request = Request.Builder()
//            .url("ws://echo.websocket.org")
            .url("$url?buildFile=$buildFile")
                .build()
        var webSocket: WebSocket? = null
        val ws = WebSocketCall.create(client, request).enqueue(object: WebSocketListener {
            override fun onOpen(ws: WebSocket, response: Response) {
                webSocket = ws
            }

            override fun onPong(p0: Buffer?) {
                println("WebSocket pong")
            }

            override fun onClose(p0: Int, p1: String?) {
                println("WebSocket closed")
            }

            override fun onFailure(ex: IOException, response: Response?) {
                Exceptions.printStackTrace(ex)
                error("WebSocket failure: ${ex.message} response: $response")
            }

            override fun onMessage(body: ResponseBody) {
                val json = body.string()
                val wsCommand = Gson().fromJson(json, WebSocketCommand::class.java)
                if (wsCommand.errorMessage != null) {
                    warn("Received error message from server: " + wsCommand.errorMessage)
                } else {
                    if (wsCommand.commandName == RemoteDependencyData.GetDependenciesData.NAME) {
                        val dd = Gson().fromJson(wsCommand.payload, RemoteDependencyData.GetDependenciesData::class.java)
                        println("Received dependency data for " + dd.projects.size + " projects")
                        if (dd.pluginDependencies.any()) {
                            println("  Plug-ins: " + dd.pluginDependencies.map { it.path }.joinToString(" "))
                        }
                        dd.projects.forEach {
                            println("  === Project: " + it.name)
                            if (dd.pluginDependencies.any()) {
                                println("    Plug-in dependencies: " + dd.pluginDependencies.joinToString(" "))
                            }
                            GraphUtil.displayGraph(it.compileDependencies,
                                    RemoteDependencyData.DependencyData::children,
                                    { d: RemoteDependencyData.DependencyData, indent: String ->
                                        println("    " + indent + d.id) })
                        }
                    } else if (wsCommand.commandName == ProgressCommand.NAME) {
                        val progress = Gson().fromJson(wsCommand.payload, ProgressCommand::class.java)
                        println(progress.message + (progress.progress ?: ""))
                    } else {
                        throw KobaltException("Unknown command: ${wsCommand.commandName} json:\n$json")
                    }
                }
            }
        })
    }
}


