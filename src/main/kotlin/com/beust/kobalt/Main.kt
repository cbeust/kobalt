package com.beust.kobalt

import com.beust.jcommander.JCommander
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.internal.*
import com.beust.kobalt.kotlin.BuildFile
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.*
import com.beust.kobalt.kotlin.ScriptCompiler2
import com.beust.kobalt.wrapper.Wrapper
import com.google.inject.Guice
import java.io.File
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject

private fun parseArgs(argv: Array<String>): Main.RunInfo {
    val args = Args()
    val result = JCommander(args)
    result.parse(*argv)
    KobaltLogger.LOG_LEVEL = args.log
    return Main.RunInfo(result, args)
}

public fun main(argv: Array<String>) {
    val result = mainNoExit(argv)
    if (result != 0) {
        System.exit(result)
    }
}

public fun mainNoExit(argv: Array<String>) : Int {
    val (jc, args) = parseArgs(argv)
    Kobalt.INJECTOR = Guice.createInjector(MainModule(args))
    return Kobalt.INJECTOR.getInstance(Main::class.java).run(jc, args)
}

private class Main @Inject constructor(
        val script2: ScriptCompiler2.IFactory,
        val plugins: Plugins,
        val taskManager: TaskManager,
        val http: Http,
        val files: KFiles,
        val executors: KobaltExecutors,
        val localRepo: LocalRepo,
        val depFactory: DepFactory,
        val checkVersions: CheckVersions,
        val github: GithubApi,
        val updateKobalt: UpdateKobalt) {

    data class RunInfo(val jc: JCommander, val args: Args)

    public fun run(jc: JCommander, args: Args) : Int {
        var result = 0
        val latestVersionFuture = github.latestKobaltVersion
        benchmark("Build", {
            println(Banner.get() + Kobalt.version + "\n")
//            runTest()
            result = runWithArgs(jc, args)
            executors.shutdown()
            debug("All done")
        })

        // Check for new version
        val latestVersionString = latestVersionFuture.get()
        val latestVersion = Versions.toLongVersion(latestVersionString)
        val current = Versions.toLongVersion(Kobalt.version)
        if (latestVersion > current) {
            "***** ".let {
                log(1, it)
                log(1, "$it New Kobalt version available: $latestVersionString")
                log(1, "$it To update, run ./kobaltw --update")
                log(1, it )
            }
        }
        return result
    }

    public class Worker<T>(val runNodes: ArrayList<T>, val n: T) : IWorker<T> {
        override val priority = 0

        override fun call() : TaskResult2<T> {
            log(2, "Running node ${n}")
            runNodes.add(n)
            return TaskResult2(n != 3, n)
        }
    }

    private fun runTest() {
        with(Topological<String>()) {
            addEdge("b1", "a1")
            addEdge("b1", "a2")
            addEdge("b2", "a1")
            addEdge("b2", "a2")
            addEdge("c1", "b1")
            addEdge("c1", "b2")
            val sorted = sort(arrayListOf("a1", "a2", "b1", "b2", "c1", "x", "y"))
            println("Sorted: ${sorted}")
        }
    }

    private val SCRIPT_JAR = "buildScript.jar"

    private fun runWithArgs(jc: JCommander, args: Args) : Int {
        var result = 0
        val p = if (args.buildFile != null) File(args.buildFile) else findBuildFile()
        args.buildFile = p.absolutePath
        val buildFile = BuildFile(Paths.get(p.absolutePath), p.name)

        if (args.init) {
            //
            // --init: create a new build project and install the wrapper
            //
            Wrapper().install()
            ProjectGenerator().run(args)
        } else if (args.usage) {
            jc.usage()
        } else {
            if (! buildFile.exists()) {
                jc.usage()
            } else {
                val context = KobaltContext(args)
                Kobalt.context = context
                val allProjects = script2.create(arrayListOf(buildFile)).findProjects()

                //
                // Force each project.directory to be an absolute path, if it's not already
                //
                allProjects.forEach {
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

                plugins.applyPlugins(context, allProjects)

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
                    checkVersions.run(allProjects)
                } else if (args.update) {
                    updateKobalt.updateKobalt()
                } else {
                    //
                    // Launch the build
                    //
                    val thisResult = taskManager.runTargets(args.targets, allProjects)
                    if (result == 0) {
                        result = thisResult
                    }
                }
            }
        }
        return result
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

