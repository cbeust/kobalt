package com.beust.kobalt.app.remote

import com.beust.kobalt.Args
import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.MainModule
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.maven.aether.Exceptions
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.warn
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Guice
import com.google.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ws.WebSocket
import okhttp3.ws.WebSocketCall
import okhttp3.ws.WebSocketListener
import okio.Buffer
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.*
import java.net.Socket
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors

fun main(argv: Array<String>) {
    Kobalt.INJECTOR = Guice.createInjector(MainModule(Args(), KobaltSettings.readSettingsXml()))
    KobaltWebSocketClient().run()
}

interface Api {
    @GET("/ping")
    fun ping() : Call<String>

    @Deprecated(message = "Replaced with /v1/getDependencies")
    @POST("/v0/getDependencies")
    fun getDependencies(@Query("buildFile") buildFile: String) : Call<List<RemoteDependencyData.GetDependenciesData>>
}

class KobaltWebSocketClient : Runnable {
    override fun run() {
        val client = OkHttpClient()
        val request = Request.Builder()
//            .url("ws://echo.websocket.org")
            .url("ws://localhost:1239/v1/getDependencyGraph?buildFile=/Users/cedricbeust/t/Kandroid/kobalt/src" +
                    "/Build.kt")
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
                        println("Received dependency data: " + dd.projects.size + " projects"
                                + " error: " + dd.errorMessage)
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

class KobaltClient : Runnable {
    private val service = Retrofit.Builder()
            .client(OkHttpClient.Builder().build())
            .baseUrl("http://localhost:1238")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)

    override fun run() {

//        val pong = service.ping().execute()
//        println("Result from ping: " + pong)

        val buildFile = Paths.get(com.beust.kobalt.SystemProperties.homeDir,
                "kotlin/klaxon/kobalt/src/Build.kt").toString()
        val dependencies = service.getDependencies(buildFile)
        val response = dependencies.execute()
        if (response.isSuccessful) {
            println("Dependencies: $response")
        } else {
            println("Error calling getDependencies: " + response.errorBody().string())
        }
        println("")
    }
}

class ServerProcess @Inject constructor(val serverFactory: KobaltServer.IFactory) {
    val SERVER_FILE = KFiles.joinDir(homeDir(KFiles.KOBALT_DOT_DIR, "kobaltServer.properties"))
    val KEY_PORT = "port"
    val executor = Executors.newFixedThreadPool(5)

    fun launch() : Int {
        var port = launchPrivate()
        while (port == 0) {
            executor.submit {
                serverFactory.create(force = true,
                        initCallback = { buildFile -> emptyList()},
                        cleanUpCallback = {})
                    .call()
            }
            //            launchServer(ProcessUtil.findAvailablePort())
            port = launchPrivate()
        }
        return port
    }

    private fun launchPrivate() : Int {
        var result = 0
        File(SERVER_FILE).let { serverFile ->
            if (serverFile.exists()) {
                val properties = Properties().apply {
                    load(FileReader(serverFile))
                }

                try {
                    Socket("localhost", result).use { socket ->
                        val outgoing = PrintWriter(socket.outputStream, true)
                        val c: String = """{ "name": "ping"}"""
                        outgoing.println(c)
                        val ins = BufferedReader(InputStreamReader(socket.inputStream))
                        var line = ins.readLine()
                        val jo = JsonParser().parse(line) as JsonObject
                        val jsonData = jo["data"]?.asString
                        val dataObject = JsonParser().parse(jsonData) as JsonObject
                        val received = JsonParser().parse(dataObject["received"].asString) as JsonObject
                        if (received["name"].asString == "ping") {
                            result = properties.getProperty(KEY_PORT).toInt()
                        }
                    }
                } catch(ex: IOException) {
                    kobaltLog(1, "Couldn't connect to current server, launching a new one")
                    Thread.sleep(1000)
                }
            }
        }

        return result
    }
}

