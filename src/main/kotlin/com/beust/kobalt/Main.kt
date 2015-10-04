package com.beust.kobalt

import com.beust.jcommander.JCommander
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.internal.*
import com.beust.kobalt.kotlin.BuildFile
import com.beust.kobalt.kotlin.ScriptCompiler
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.*
import com.beust.kobalt.plugin.java.SystemProperties
import com.beust.kobalt.plugin.publish.JCenterApi
import com.beust.kobalt.plugin.publish.UnauthenticatedJCenterApi
import com.beust.kobalt.wrapper.Wrapper
import com.google.inject.Guice
import java.io.File
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject

val INJECTOR = Guice.createInjector(MainModule())

public fun main(argv: Array<String>) {
    INJECTOR.getInstance(Main::class.java).run(argv)
}

private class Main @Inject constructor(
        val scriptCompilerFactory: ScriptCompiler.IFactory,
        val plugins: Plugins,
        val taskManager: TaskManager,
        val http: Http,
        val files: KFiles,
        val executors: KobaltExecutors,
        val localRepo: LocalRepo,
        val depFactory: DepFactory,
        val checkVersions: CheckVersions,
        val jcenter: UnauthenticatedJCenterApi)
    : KobaltLogger {

    data class RunInfo(val jc: JCommander, val args: Args)

    public fun run(argv: Array<String>) {
        // Check for new version
        // Commented out until I can find a way to get the latest available download
        // from bintray. Right now, it always returns all the versions uploaded, not
        // just the one I mark
//        val p = jcenter.kobaltPackage
//        val current = Versions.toLongVersion(Kobalt.version)
//        val remote = Versions.toLongVersion(p.latestPublishedVersion)
//        if (remote > current) {
//            log(1, "*****")
//            log(1, "***** New Kobalt version available: ${p.latestPublishedVersion}")
//            log(1, "*****")
//        }

        benchmark("Build", {
            println(Banner.get() + Kobalt.version + "\n")
//            runTest()
            val (jc, args) = parseArgs(argv)
            runWithArgs(jc, args)
            executors.shutdown()
            debug("All done")
        })
    }

    public class Worker<T>(val runNodes: ArrayList<T>, val n: T) : IWorker<T>, KobaltLogger {
        override val priority = 0

        override fun call() : TaskResult2<T> {
            log(2, "Running node ${n}")
            runNodes.add(n)
            return TaskResult2(n != 3, n)
        }
    }

    private fun runTest() {
        val dg = Topological<String>()
        dg.addEdge("b1", "a1")
        dg.addEdge("b1", "a2")
        dg.addEdge("b2", "a1")
        dg.addEdge("b2", "a2")
        dg.addEdge("c1", "b1")
        dg.addEdge("c1", "b2")
        val sorted = dg.sort(arrayListOf("a1", "a2", "b1", "b2", "c1", "x", "y"))
        println("Sorted: ${sorted}")
    }

    private fun parseArgs(argv: Array<String>): RunInfo {
        val args = Args()
        val result = JCommander(args)
        result.parse(*argv)
        KobaltLogger.LOG_LEVEL = args.log
        return RunInfo(result, args)
    }

    private val SCRIPT_JAR = "buildScript.jar"

    private fun runWithArgs(jc: JCommander, args: Args) {
        val p = if (args.buildFile != null) File(args.buildFile) else findBuildFile()
        args.buildFile = p.absolutePath
        val buildFile = BuildFile(Paths.get(p.absolutePath), p.name)

        if (args.init) {
            //
            // --init: create a new build project and install the wrapper
            //
            Wrapper().install()
            ProjectGenerator().run(args)
        } else {
            if (! buildFile.exists()) {
                jc.usage()
            } else {
                // Install all the plugins found
                plugins.installDynamicPlugins(arrayListOf(buildFile))

                // Compile the build script
                val output = scriptCompilerFactory.create(plugins.pluginJarFiles,
                        { n: String, j: File? ->
                            plugins.instantiateClassName(n, j)
                        })
                    .compile(buildFile, buildFile.lastModified(), KFiles.findBuildScriptLocation(buildFile, SCRIPT_JAR))

                //
                // Force each project.directory to be an absolute path, if it's not already
                //
                output.projects.forEach {
                    val fd = File(it.directory)
                    if (! fd.isAbsolute) {
                        it.directory =
                            if (args.buildFile != null) {
                                KFiles.findDotDir(File(args.buildFile)).parentFile.absolutePath
                            } else {
                                fd.absolutePath
                            }
                    }
                }

                plugins.applyPlugins(KobaltContext(args), output.projects)

                if (args.tasks) {
                    //
                    // List of tasks
                    //
                    val sb = StringBuffer("List of tasks\n")
                    Plugins.plugins.forEach { plugin ->
                        if (plugin.tasks.size() > 0) {
                            sb.append("\n  ===== ${plugin.name} =====\n")
                            plugin.tasks.forEach { task ->
                                sb.append("    ${task.name}\t\t${task.doc}\n")
                            }
                        }
                    }
                    println(sb.toString())
                } else if (args.checkVersions) {
                    checkVersions.run(output.projects)
                } else {
                    //
                    // Launch the build
                    //
                    taskManager.runTargets(args.targets, output.projects)
                }
            }
        }
    }

    private fun findBuildFile(): File {
        val files = arrayListOf("Build.kt", "build.kobalt", KFiles.src("build.kobalt"),
                KFiles.src("Build.kt"))
        try {
            return files.map {
                File(SystemProperties.currentDir, it)
            }.first {
                it.exists()
            }
        } catch(ex: NoSuchElementException) {
            return File("Build.kt")
        }
    }
}

