package com.beust.kobalt.app.remote

import com.beust.kobalt.api.Project
import com.beust.kobalt.homeDir
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.maven.aether.Exceptions
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import java.io.File
import java.io.FileWriter
import java.lang.management.ManagementFactory
import java.util.*
import java.util.concurrent.Callable
import javax.annotation.Nullable

/**
 * Launch a Kobalt server. If @param{force} is specified, a new server will be launched even if one was detected
 * to be already running (from the ~/.kobalt/kobaltServer.properties file).
 *
 * The callbacks are used to initialize and clean up the state before and after each command, so that Kobalt's state
 * can be properly reset, making the server reentrant.
 *
 * To enable websocket debugging, uncomment the "debug" <root> tag in logback.xml.
 */
class KobaltServer @Inject constructor(@Assisted val force: Boolean, @Assisted @Nullable val givenPort: Int?,
        @Assisted val initCallback: (String) -> List<Project>,
        @Assisted val cleanUpCallback: () -> Unit,
        val pluginInfo : PluginInfo) : Callable<Int> {

    interface IFactory {
        fun create(force: Boolean, givenPort: Int? = null,
                initCallback: (String) -> List<Project>,
                cleanUpCallback: () -> Unit) : KobaltServer
    }

    companion object {
        /**
         * Default response sent for calls that don't return a payload.
         */
        val OK = "ok"
    }

//    var outgoing: PrintWriter? = null

    interface IServer {
        fun run(port: Int)
    }

    override fun call() : Int {
        val port = givenPort ?: ProcessUtil.findAvailablePort(1234)
        try {
            if (createServerFile(port, force)) {
                kobaltLog(1, "KobaltServer listening on port $port")
//                OldServer(initCallback, cleanUpCallback).run(port)
//                JerseyServer(initCallback, cleanUpCallback).run(port)
                SparkServer(initCallback, cleanUpCallback, pluginInfo).run(port)
//                WasabiServer(initCallback, cleanUpCallback).run(port)
            }
        } catch(ex: Exception) {
            Exceptions.printStackTrace(ex)
        } finally {
//            deleteServerFile()
        }
        return port
    }

    val SERVER_FILE = KFiles.joinDir(homeDir(KFiles.KOBALT_DOT_DIR, "kobaltServer.properties"))
    val KEY_PORT = "port"
    val KEY_PID = "pid"

    private fun createServerFile(port: Int, force: Boolean) : Boolean {
        if (File(SERVER_FILE).exists() && ! force) {
            kobaltLog(1, "Server file $SERVER_FILE already exists. Another server is probably already running.")
            kobaltLog(1, "Relaunch this server with --force to run this server anyway and take over the port.")
            return false
        } else {
            val processName = ManagementFactory.getRuntimeMXBean().name
            val pid = processName.split('@')[0]
            Properties().apply {
                put(KEY_PORT, port.toString())
                put(KEY_PID, pid)
            }.store(FileWriter(SERVER_FILE), "")
            kobaltLog(2, "KobaltServer created $SERVER_FILE")
            return true
        }
    }

    private fun deleteServerFile() {
        kobaltLog(1, "KobaltServer deleting $SERVER_FILE")
        File(SERVER_FILE).delete()
    }
}

