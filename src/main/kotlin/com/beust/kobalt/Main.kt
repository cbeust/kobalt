package com.beust.kobalt

import com.beust.jcommander.JCommander
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.PluginTask
import com.beust.kobalt.api.Project
import com.beust.kobalt.app.*
import com.beust.kobalt.app.remote.DependencyData
import com.beust.kobalt.app.remote.KobaltClient
import com.beust.kobalt.app.remote.KobaltServer
import com.beust.kobalt.internal.Gc
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.TaskManager
import com.beust.kobalt.internal.build.BuildFile
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.Http
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.*
import com.google.common.collect.HashMultimap
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject

fun main(argv: Array<String>) {
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

fun mainNoExit(argv: Array<String>): Int {
    val (jc, args) = parseArgs(argv)
    Kobalt.init(MainModule(args, KobaltSettings.readSettingsXml()))
    val result = Kobalt.INJECTOR.getInstance(Main::class.java).run {
        val runResult = run(jc, args, argv)
        pluginInfo.cleanUp()
        executors.shutdown()
        runResult
    }
    return result
}

private class Main @Inject constructor(
        val buildFileCompilerFactory: BuildFileCompiler.IFactory,
        val plugins: Plugins,
        val taskManager: TaskManager,
        val http: Http,
        val files: KFiles,
        val executors: KobaltExecutors,
        val dependencyManager: DependencyManager,
        val checkVersions: CheckVersions,
        val github: GithubApi2,
        val updateKobalt: UpdateKobalt,
        val client: KobaltClient,
        val pluginInfo: PluginInfo,
        val dependencyData: DependencyData,
        val projectGenerator: ProjectGenerator,
        val serverFactory: KobaltServer.IFactory,
        val resolveDependency: ResolveDependency) {

    data class RunInfo(val jc: JCommander, val args: Args)

    private fun installCommandLinePlugins(args: Args) : ClassLoader {
        var pluginClassLoader = javaClass.classLoader
        val dependencies = arrayListOf<IClasspathDependency>()
        args.pluginIds?.let {
            // We want this call to go to the network if no version was specified, so set localFirst to false
            dependencies.addAll(it.split(",").map { dependencyManager.create(it) })
        }
        args.pluginJarFiles?.let {
            dependencies.addAll(it.split(",").map { FileDependency(it) })
        }
        if (dependencies.size > 0) {
            val urls = dependencies.map { it.jarFile.get().toURI().toURL() }
            pluginClassLoader = URLClassLoader(urls.toTypedArray())
            plugins.installPlugins(dependencies, pluginClassLoader)
        }

        return pluginClassLoader
    }

    fun run(jc: JCommander, args: Args, argv: Array<String>): Int {

        //
        // Install plug-ins requested from the command line
        //
        val pluginClassLoader = installCommandLinePlugins(args)

        // --listTemplates
        if (args.listTemplates) {
            Templates().displayTemplates(pluginInfo)
            return 0
        }

        if (args.client) {
            client.run()
            return 0
        }

        var result = 0
        val latestVersionFuture = github.latestKobaltVersion

        val seconds = benchmarkSeconds {
            try {
                result = runWithArgs(jc, args, argv, pluginClassLoader)
            } catch(ex: Throwable) {
                error("", ex.cause ?: ex)
                result = 1
            }
        }

        if (! args.update) {
            log(1, if (result != 0) "BUILD FAILED: $result" else "BUILD SUCCESSFUL ($seconds seconds)")

            updateKobalt.checkForNewVersion(latestVersionFuture)
        }
        return result
    }

    //    public fun runTest() {
    //        val file = File("src\\main\\resources\\META-INF\\plugin.ml")
    //    }

    private fun runWithArgs(jc: JCommander, args: Args, argv: Array<String>, pluginClassLoader: ClassLoader): Int {
//        val file = File("/Users/beust/.kobalt/repository/com/google/guava/guava/19.0-rc2/guava-19.0-rc2.pom")
//        val md5 = Md5.toMd5(file)
//        val md52 = MessageDigest.getInstance("MD5").digest(file.readBytes()).toHexString()
        var result = 0
        val p = if (args.buildFile != null) File(args.buildFile) else findBuildFile()
        args.buildFile = p.absolutePath
        val buildFile = BuildFile(Paths.get(p.absolutePath), p.name)

        if (!args.update) {
            println(AsciiArt.banner + Kobalt.version + "\n")
        }

        if (args.templates != null) {
            //
            // --init: create a new build project and install the wrapper
            // Make sure the wrapper won't call us back with --noLaunch
            //
            projectGenerator.run(args, pluginClassLoader)
            // The wrapper has to call System.exit() in order to set the exit code,
            // so make sure we call it last (or possibly launch it in a separate JVM).
            com.beust.kobalt.wrapper.Main.main(arrayOf("--noLaunch") + argv)
        } else if (args.usage) {
            jc.usage()
        } else if (args.serverMode) {
            // --server
            val port = serverFactory.create(args.force, args.port,
                    { buildFile -> initForBuildFile(BuildFile(Paths.get(buildFile), buildFile), args)},
                    { cleanUp() })
                .call()
        } else {
            // Options that don't need Build.kt to be parsed first
            if (args.gc) {
                Gc().run()
            } else if (args.update) {
                // --update
                updateKobalt.updateKobalt()
            } else {
                //
                // Everything below requires to parse the build file first
                //
                if (!buildFile.exists()) {
                    error(buildFile.path.toFile().path + " does not exist")
                } else {

                    val allProjects = initForBuildFile(buildFile, args)

                    // DONOTCOMMIT
//                    val data = dependencyData.dependenciesDataFor(homeDir("kotlin/klaxon/kobalt/src/Build.kt"), Args())
//                    println("Data: $data")

                    if (args.projectInfo) {
                        // --projectInfo
                        allProjects.forEach {
                            resolveDependency.run(it.compileDependencies.map {it.id})
                        }
                    } else if (args.dependencies != null) {
                        // --resolve
                        resolveDependency.run(args.dependencies!!.split(",").toList())
                    } else if (args.tasks) {
                        // --tasks
                        displayTasks()
                    } else if (args.checkVersions) {
                        // --checkVersions
                        checkVersions.run(allProjects)
                    } else if (args.download) {
                        // -- download
                        updateKobalt.downloadKobalt()
                    } else {
                        //
                        // Launch the build
                        //
                        val runTargetResult = taskManager.runTargets(args.targets, allProjects)
                        if (result == 0) {
                            result = runTargetResult.exitCode
                        }

                        // Shutdown all plug-ins
                        plugins.shutdownPlugins()

                        log(3, "Timings:\n  " + runTargetResult.messages.joinToString("\n  "))
                    }
                }
            }
        }
        return result
    }

    private fun cleanUp() {
        pluginInfo.cleanUp()
        taskManager.cleanUp()
    }

    private fun initForBuildFile(buildFile: BuildFile, args: Args): List<Project> {
        val findProjectResult = buildFileCompilerFactory.create(listOf(buildFile), pluginInfo)
                .compileBuildFiles(args)
        if (! findProjectResult.taskResult.success) {
            throw KobaltException("Couldn't compile build file: "
                    + findProjectResult.taskResult.errorMessage)
        }

        val allProjects = findProjectResult.projects

        //
        // Now that we have projects, add all the repos from repo contributors that need a Project
        //
        allProjects.forEach { project ->
            pluginInfo.repoContributors.forEach {
                it.reposFor(project).forEach {
                    Kobalt.addRepo(it)
                }
            }
        }

        //
        // Run all the dependencies through the IDependencyInterceptors
        //
        runClasspathInterceptors(allProjects)

        log(2, "Final list of repos:\n  " + Kobalt.repos.joinToString("\n  "))

        //
        // Call apply() on all plug-ins now that the repos are set up
        //
        plugins.applyPlugins(Kobalt.context!!, allProjects)

        return allProjects
    }

    private fun displayTasks() {
        //
        // List of tasks, --tasks
        //
        val tasksByPlugins = HashMultimap.create<String, PluginTask>()
        taskManager.annotationTasks.forEach {
            tasksByPlugins.put(it.plugin.name, it)
        }
        val sb = StringBuffer("List of tasks\n")
        tasksByPlugins.keySet().forEach { name ->
            sb.append("\n  " + AsciiArt.horizontalDoubleLine + " $name "
                    + AsciiArt.horizontalDoubleLine + "\n")
            tasksByPlugins[name].distinctBy {
                it.name
            }.sortedBy {
                it.name
            }.forEach { task ->
                sb.append("    ${task.name}\t\t${task.doc}\n")
            }
        }

        println(sb.toString())
    }

    private fun runClasspathInterceptors(allProjects: List<Project>) {
        allProjects.forEach {
            runClasspathInterceptors(it, it.compileDependencies)
            runClasspathInterceptors(it, it.compileProvidedDependencies)
            runClasspathInterceptors(it, it.compileRuntimeDependencies)
            runClasspathInterceptors(it, it.testProvidedDependencies)
            runClasspathInterceptors(it, it.testDependencies)
            runClasspathInterceptors(it, it.nativeDependencies)
        }
    }

    private fun runClasspathInterceptors(project: Project, dependencies: ArrayList<IClasspathDependency>)
            = with(dependencies) {
                if (pluginInfo.classpathInterceptors.size > 0) {
                    val deps = interceptDependencies(project, pluginInfo, this)
                    clear()
                    addAll(deps)
                } else {
                    this
                }
            }

    private fun interceptDependencies(project: Project, pluginInfo: PluginInfo,
            dependencies: ArrayList<IClasspathDependency>) : ArrayList<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        pluginInfo.classpathInterceptors.forEach {
            result.addAll(it.intercept(project, dependencies))
        }
        return result
    }

    private fun findBuildFile() : File {
        val deprecatedLocation = File(Constants.BUILD_FILE_NAME)
        val result: File =
            if (deprecatedLocation.exists()) {
                warn(Constants.BUILD_FILE_NAME + " is in a deprecated location, please move it to "
                        + Constants.BUILD_FILE_DIRECTORY)
                deprecatedLocation
            } else {
                File(KFiles.joinDir(Constants.BUILD_FILE_DIRECTORY, Constants.BUILD_FILE_NAME))
            }
        return result
    }
}
