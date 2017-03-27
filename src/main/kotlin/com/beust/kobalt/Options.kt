package com.beust.kobalt

import com.beust.jcommander.JCommander
import com.beust.kobalt.app.ProjectGenerator
import com.beust.kobalt.app.UpdateKobalt
import com.beust.kobalt.app.remote.KobaltServer
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.wrapper.Main
import com.google.inject.Inject
import java.io.File
import java.nio.file.Paths

open class Option(open val enabled: () -> Boolean, open val action: () -> Unit,
        open val requireBuildFile: Boolean = true)
class OptionalBuildOption(override val enabled: () -> Boolean, override val action: () -> Unit)
    : Option(enabled, action, false)

class Options @Inject constructor(
        val projectGenerator: ProjectGenerator,
        val pluginInfo: PluginInfo,
        val serverFactory: KobaltServer.IFactory,
        val updateKobalt: UpdateKobalt,
        val taskManager: TaskManager
        ) {

    fun run(jc: JCommander, args: Args, argv: Array<String>) {
        val p = if (args.buildFile != null) File(args.buildFile) else KFiles.findBuildFile()
        val buildFile = BuildFile(Paths.get(p.absolutePath), p.name)
        var pluginClassLoader = javaClass.classLoader
        val options = arrayListOf<Option>(
                OptionalBuildOption( { -> args.templates != null }, {
                    //
                    // --init: create a new build project and install the wrapper
                    // Make sure the wrapper won't call us back with --noLaunch
                    //
                    projectGenerator.run(args, pluginClassLoader)
                    // The wrapper has to call System.exit() in order to set the exit code,
                    // so make sure we call it last (or possibly launch it in a separate JVM).
                    Main.main(arrayOf("--noLaunch") + argv)
                }),
                OptionalBuildOption( { -> args.usage }, { jc.usage() }),
                OptionalBuildOption( { -> args.update }, {
                    /* --update*/
                    updateKobalt.updateKobalt() }),
                OptionalBuildOption( { -> args.serverMode }, {
                    // --server
                    val port = serverFactory.create(args.force, args.port, { cleanUp() }).call()
                })
        )

        options.forEach {
            if (it.enabled()) {
                if ((it.requireBuildFile && buildFile.exists()) || ! it.requireBuildFile) {
                    it.action()
                } else if (it.requireBuildFile && ! buildFile.exists()) {
                    throw IllegalArgumentException("Couldn't find a build file")
                }
            }
        }
    }

    private fun cleanUp() {
        pluginInfo.cleanUp()
        taskManager.cleanUp()
    }

}