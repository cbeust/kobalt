package com.beust.kobalt

import com.beust.jcommander.*
import com.beust.kobalt.api.*
import com.beust.kobalt.internal.*
import com.beust.kobalt.internal.remote.*
import com.beust.kobalt.kotlin.*
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.*
import com.google.inject.*
import java.io.*
import java.nio.file.*
import java.util.*
import javax.inject.Inject
import kotlin.util.*

public fun main(argv: Array<String>) {
    val result = mainNoExit(argv)
    if (result != 0) {
        System.exit(result)
    }
}

private fun parseArgs(argv: Array<String>): Main.RunInfo {
    val args = Args()
    val result = JCommander(args)
    result.parse(*argv)
    KobaltLogger.LOG_LEVEL = args.log
    return Main.RunInfo(result, args)
}


public fun mainNoExit(argv: Array<String>): Int {
    val (jc, args) = parseArgs(argv)
    Kobalt.INJECTOR = Guice.createInjector(MainModule(args))
    return Kobalt.INJECTOR.getInstance(Main::class.java).run(jc, args, argv)
}

private class Main @Inject constructor(
        val buildFileCompilerFactory: BuildFileCompiler.IFactory,
        val plugins: Plugins,
        val taskManager: TaskManager,
        val http: Http,
        val files: KFiles,
        val executors: KobaltExecutors,
        val localRepo: LocalRepo,
        val depFactory: DepFactory,
        val checkVersions: CheckVersions,
        val github: GithubApi,
        val updateKobalt: UpdateKobalt,
        val client: KobaltClient,
        val server: KobaltServer,
        val pluginInfo: PluginInfo,
        val projectGenerator: ProjectGenerator,
        val resolveDependency: ResolveDependency) {

    data class RunInfo(val jc: JCommander, val args: Args)

    private fun addReposFromContributors(project: Project?) =
            pluginInfo.repoContributors.forEach {
                it.reposFor(project).forEach {
                    Kobalt.addRepo(it.toString())
                }
            }

    public fun run(jc: JCommander, args: Args, argv: Array<String>): Int {
//        github.uploadRelease("kobalt", "0.101", File("/Users/beust/t/a.zip"))

        //
        // Add all the repos from repo contributors (at least those that return values without a Project)
        //
        addReposFromContributors(null)

        //
        // Add all the plugins read in plugin.xml to the Plugins singleton, so that code
        // in the build file that calls Plugins.findPlugin() can find them (code in the
        // build file do not have access to the KobaltContext).
        //
        pluginInfo.plugins.forEach { Plugins.addPluginInstance(it) }

        if (args.client) {
            client.run()
            return 0
        }

        var result = 0
        val latestVersionFuture = github.latestKobaltVersion
        val seconds = 
            measureTimeMillis {
                try {
                    result = runWithArgs(jc, args, argv)
                } catch(ex: KobaltException) {
                    error(ex.message ?: "", ex)
                    result = 1
                } finally {
                    executors.shutdown()
                }
            }

        log(1, if (result != 0) "BUILD FAILED: $result" else "BUILD SUCCESSFUL ($seconds seconds)")

        // Check for new version
        val latestVersionString = latestVersionFuture.get()
        val latestVersion = Versions.toLongVersion(latestVersionString)
        val current = Versions.toLongVersion(Kobalt.version)
        if (latestVersion > current) {
            listOf("", "New Kobalt version available: $latestVersionString",
                    "To update, run ./kobaltw --update", "").forEach {
                log(1, "**** $it")
            }
        }
        return result
    }


    //    public fun runTest() {
    //        val file = File("src\\main\\resources\\META-INF\\plugin.ml")
    //    }

    private fun runWithArgs(jc: JCommander, args: Args, argv: Array<String>): Int {
        var result = 0
        val p = if (args.buildFile != null) File(args.buildFile) else findBuildFile()
        args.buildFile = p.absolutePath
        val buildFile = BuildFile(Paths.get(p.absolutePath), p.name)

        if (!args.update) {
            println(AsciiArt.banner + Kobalt.version + "\n")
        }

        if (args.init) {
            //
            // --init: create a new build project and install the wrapper
            // Make sure the wrapper won't call us back with --noLaunch
            //
            com.beust.kobalt.wrapper.Main.main(arrayOf("--noLaunch") + argv)
            projectGenerator.run(args)
        } else if (args.usage) {
            jc.usage()
        } else if (args.serverMode) {
            server.run()
        } else {
            if (!buildFile.exists()) {
                error(buildFile.path.toFile().path + " does not exist")
            } else {
                val allProjects = buildFileCompilerFactory.create(listOf(buildFile), pluginInfo)
                        .compileBuildFiles(args)

                //
                // Now that we have projects, add all the repos from repo contributors that need a Project
                //
                allProjects.forEach { addReposFromContributors(it) }

                log(2, "Final list of repos:\n  " + Kobalt.repos.joinToString("\n  "))

                if (args.dependency != null) {
                    // --resolve
                    resolveDependency.run(args.dependency as String)
                } else if (args.tasks) {
                    //
                    // List of tasks
                    //
                    val sb = StringBuffer("List of tasks\n")
                    Plugins.plugins.forEach { plugin ->
                        if (plugin.tasks.size > 0) {
                            sb.append("\n  ===== ${plugin.name} =====\n")
                            plugin.tasks.distinctBy {
                                it.name
                            }.forEach { task ->
                                sb.append("    ${task.name}\t\t${task.doc}\n")
                            }
                        }
                    }
                    println(sb.toString())
                } else if (args.checkVersions) {
                    checkVersions.run(allProjects)
                } else if (args.download) {
                    updateKobalt.downloadKobalt()
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
