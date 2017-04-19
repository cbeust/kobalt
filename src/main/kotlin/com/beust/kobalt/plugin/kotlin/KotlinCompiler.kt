package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.internal.*
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.*
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.ICReporter
import org.jetbrains.kotlin.incremental.makeIncrementally
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.properties.Delegates

/**
 * @author Cedric Beust <cedric@beust.com>
 * @since 08 03, 2015
 */
@Singleton
class KotlinCompiler @Inject constructor(
        val files: KFiles,
        val cliArgs: Args,
        val dependencyManager: DependencyManager,
        val executors: KobaltExecutors,
        val settings: KobaltSettings,
        val jvmCompiler: JvmCompiler,
        val compilerUtils: CompilerUtils,
        val kobaltLog: ParallelLogger,
        val jvm: Jvm) {

    val compilerAction = object: ICompilerAction {
        override fun compile(project: Project?, info: CompilerActionInfo): TaskResult {
            val projectName = project?.name
            val version = kotlinVersion(project)
            var filesToCompile = 0
            if (! info.outputDir.path.endsWith("ript.jar")) {
                // Don't display the message if compiling Build.kt
                if (info.sourceFiles.isNotEmpty()) {
                    filesToCompile =
                            info.sourceFiles.map(::File).map {
                                if (it.isDirectory) KFiles.findRecursively(it).size else 1
                            }.reduce { a, b ->
                                a + b
                            }
                    kobaltLog.log(projectName ?: "", 1,
                            "  Kotlin $version compiling " + Strings.pluralizeAll(filesToCompile, "file"))
                }
            }
            val cp = compilerFirst(info.dependencies.map { it.jarFile.get() })
            val infoDir = info.directory
            val outputDir =
                if (infoDir != null) {
                    KFiles.joinDir(infoDir, info.outputDir.path)
                } else {
                    info.outputDir.path
                }
            // kotlinc can accept a jar file as -d (which is super convenient) so only
            // create a directory if the output is not a jar file
            if (! outputDir.endsWith(".jar")) {
                File(outputDir).mkdirs()
            } else {
                File(outputDir).parentFile.mkdirs()
            }
            val classpath = cp.joinToString(File.pathSeparator)
            val allArgs = arrayListOf(
                    "-d", outputDir,
                    "-classpath", classpath,
                    *(info.compilerArgs.toTypedArray()),
                    *(info.sourceFiles.toTypedArray())
            )

            // Get rid of annoying and useless warning
            if (! info.compilerArgs.contains("-no-stdlib")) {
                allArgs.add("-no-stdlib")
            }

            // If the Kotlin compiler version in settings.xml is different from the default, we
            // need to spawn a Kotlin compiler in a separate process. Otherwise, we can just invoke
            // the K2JVMCompiler class directly
            val actualVersion = kotlinVersion(project)

            if (settings.kobaltCompilerSeparateProcess || actualVersion != Constants.KOTLIN_COMPILER_VERSION
                    || info.compilerSeparateProcess) {
                return invokeCompilerInSeparateProcess(classpath, info, actualVersion, project)

            } else {
                return invokeCompilerDirectly(project, projectName ?: "kobalt-" + Random().nextInt(), outputDir,
                        info, classpath, filesToCompile)
            }
        }

        private fun invokeCompilerInSeparateProcess(classpath: String, info: CompilerActionInfo,
                compilerVersion: String, project: Project?): TaskResult {
            val java = jvm.javaExecutable

            val compilerClasspath = compilerDep(compilerVersion).jarFile.get().path + File.pathSeparator +
                    compilerEmbeddableDependencies(null, compilerVersion).map { it.jarFile.get().path }
                            .joinToString(File.pathSeparator)
            val xFlagsString = listOf(kotlinConfig(project)?.args?.joinToString(" "),
                    settings.kobaltCompilerFlags)
                .filterNotNull()
                .joinToString(" ")

            val xFlagsArray = xFlagsString.split(" ").toTypedArray() ?: emptyArray()
            val newArgs = listOf(
                    "-classpath", compilerClasspath,
                    K2JVMCompiler::class.java.name,
                    *info.compilerArgs.toTypedArray(),
                    "-classpath", classpath,
                    "-d", info.outputDir.absolutePath,
                    *xFlagsArray,
                    *info.sourceFiles.toTypedArray())
                .filter { ! it.isEmpty() }

            log(2, "  Invoking separate kotlinc:\n  " + java!!.absolutePath + " " + newArgs.joinToString(" "))

            val result = NewRunCommand(RunCommandInfo().apply {
                command = java.absolutePath
                args = newArgs
                directory = File(".")
//                // The Kotlin compiler issues warnings on stderr :-(
                useErrorStreamAsErrorIndicator = false
//                containsErrors = {
//                    errors: List<String> -> errors.any { it.contains("rror")}
//                }
            }).invoke()
            return TaskResult(result == 0, errorMessage = "Error while compiling")
        }

        private fun invokeCompilerDirectly(project: Project?, projectName: String, outputDir: String?,
                info: CompilerActionInfo, classpathString: String, filesToCompile: Int): TaskResult {
            val sourceFiles = info.sourceFiles
            val friends = info.friendPaths.toTypedArray()

            // Collect the compiler args from kotlinCompiler{} and from settings.xml and parse them
            val args2 =
                    info.compilerArgs +
                    (kotlinConfig(project)?.args ?: arrayListOf<String>()) +
                    (settings.kobaltCompilerFlags?.split(" ") ?: listOf<String>())
            val args = K2JVMCompilerArguments()
            val compiler = K2JVMCompiler()
            compiler.parseArguments(args2.toTypedArray(), args)

            // Override important arguments with our values
            args.apply {
                moduleName = projectName
                destination = outputDir
                classpath = classpathString
                freeArgs = sourceFiles
                friendPaths = friends
            }

            /**
             * ~/.config/kobalt/settings.xml allows users to specify -Xflags in the
             * <kobaltCompilerFlags> tag. Map each of these string flags to the boolean
             * found in the args class
             */
            fun updateArgsWithCompilerFlags(args: K2JVMCompilerArguments, settings: KobaltSettings) {
                val flags = settings.kobaltCompilerFlags?.split(" ")
                flags?.forEach {
                    if (it.startsWith("-X")) when(it.substring(2)) {
                        "no-call-assertions" -> args.noCallAssertions = true
                        "no-param-assertions" -> args.noParamAssertions = true
                        "no-optimize" -> args.noOptimize = true
                        "report-perf" -> args.reportPerf = true
                        "multifile-parts-inherit" -> args.inheritMultifileParts = true
                        "allow-kotlin-package" -> args.allowKotlinPackage = true
                        "skip-metadata-version-check" -> args.skipMetadataVersionCheck = true
                        "skip-runtime-version-check" -> args.skipRuntimeVersionCheck = true
                        "single-module" -> args.singleModule = true
                        "load-builtins-from-dependencies" -> args.loadBuiltInsFromDependencies = true

                        "coroutines=enable" -> args.coroutinesEnable = true
                        "coroutines=warn" -> args.coroutinesWarn = true
                        "coroutines=error" -> args.coroutinesError = true
                        "no-inline" -> args.noInline = true
                        "multi-platform" -> args.multiPlatform = true
                        "no-check-impl" -> args.noCheckImpl = true
                        else -> warn("Unknown Kotlin compiler flag found in config.xml: $it")
                    }
                }
            }

            updateArgsWithCompilerFlags(args, settings)

            fun logk(level: Int, message: CharSequence) = kobaltLog.log(projectName, level, message)

            fun pluginClasspaths(args: K2JVMCompilerArguments) : String {
                var result = ""
                args.pluginClasspaths?.forEach {
                    result += " -Xplugin " + it
                }
                args.pluginOptions?.let {
                    result += " -P "
                    result += it.joinToString(",")
                }
                return result
            }

            logk(2, "  Invoking K2JVMCompiler with arguments: kotlinc "
                    + if (args.skipMetadataVersionCheck) " -Xskip-metadata-version-check" else ""
                    + " -d " + args.destination
                    + " -classpath " + args.classpath
                    + pluginClasspaths(args)
                    + " " + sourceFiles.joinToString(" "))
            logk(2, "    Additional kotlinc arguments: "
                    + " -moduleName " + args.moduleName
                    + " -friendPaths " + args.friendPaths.joinToString(";"))
            val collector = object : MessageCollector {
                override fun clear() {
                    throw UnsupportedOperationException("not implemented")
                }

                override fun hasErrors(): Boolean {
                    throw UnsupportedOperationException("not implemented")
                }

                fun dump(location: CompilerMessageLocation?, s: String) =
                    if (location != null && location.lineContent != null) {
                        with(location) {
                            "$lineContent\n$path:$line:$column $s"
                        }
                    } else {
                        s
                    }

                override fun report(severity: CompilerMessageSeverity,
                        message: String, location: CompilerMessageLocation) {
                    if (severity.isError) {
                        "Couldn't compile file: ${dump(location, message)}".let { fullMessage ->
                            throw KobaltException(fullMessage)
                        }
                    } else if (severity == CompilerMessageSeverity.WARNING && KobaltLogger.LOG_LEVEL >= 2) {
                        warn(dump(location, message))
                    } else if (severity == CompilerMessageSeverity.INFO && KobaltLogger.LOG_LEVEL >= 2) {
                        logk(2, dump(location, message))
                    }
                }
            }
//
//            System.setProperty("kotlin.incremental.compilation", "true")
//            // TODO: experimental should be removed as soon as it becomes standard
//            System.setProperty("kotlin.incremental.compilation.experimental", "true")

            val result =
                    if (cliArgs.noIncrementalKotlin || Kobalt.context?.internalContext?.noIncrementalKotlin ?: false) {
                        log(2, "  Kotlin incremental compilation is disabled")
                        val duration = benchmarkMillis {
                            compiler.exec(collector, Services.Builder().build(), args)
                        }
                        log(1, "  Regular compilation time: ${duration.first} ms")
                        TaskResult(duration.second == ExitCode.OK)
                    } else {
                        log(1, "  Kotlin incremental compilation is enabled")
                        val start = System.currentTimeMillis()
                        val duration = benchmarkMillis {
                            compileIncrementally(filesToCompile, sourceFiles, outputDir, info, args, collector)
                        }
                        log(1, "  Incremental compilation time: ${duration.first} ms")
                        TaskResult()
                    }
            return result
        }

        private fun compileIncrementally(filesToCompile: Int, sourceFiles: List<String>, outputDir: String?,
                info: CompilerActionInfo,
                args: K2JVMCompilerArguments,
                collector: MessageCollector) {
            val compiledFiles = arrayListOf<File>()
            val reporter = object : ICReporter {
                override fun pathsAsString(files: Iterable<File>): String {
                    return files.joinToString { it.absolutePath }
//                    return files.joinToString { it.relativeTo(workingDir).path }
                }

                override fun report(message: () -> String) {
                    log(3, "    ICReport: ${message()}")
                }

                override fun reportCompileIteration(sourceFiles: Collection<File>, exitCode: ExitCode) {
                    log(3, "    ICCompileIteration Compiled files: ${pathsAsString(sourceFiles)}")
                    compiledFiles.addAll(sourceFiles)
                }
            }
            incrementalCompile(sourceFiles, File(outputDir), info.forceRecompile, args, collector, reporter)
            if (filesToCompile > compiledFiles.size) {
                log(1, "  Actual files that needed to be compiled: " + compiledFiles.size)
            }
        }

        /**
         * Invoke the incremental compiler.
         */
        fun incrementalCompile(sourceFiles: List<String>, outputDirectory: File, forceRecompile: Boolean,
                args: K2JVMCompilerArguments,
                messageCollector: MessageCollector, reporter: ICReporter) {
            // If asked to force recompile, create a brand new cachesDir, otherwise reuse the existing one
            val cachesDir =
                if (forceRecompile) Files.createTempDirectory("kobalt-").toFile()
                else File(outputDirectory.parent, outputDirectory.name + "-ic-caches")

            val sourceRoots = sourceFiles.map(::File).map { if (it.isFile) it.parentFile else it }.toSet()
            try {
                makeIncrementally(cachesDir, sourceRoots, args, messageCollector, reporter)
            } catch(ex: Exception) {
                throw KobaltException(ex.message, ex)
            }
        }

        /**
         * Invoke the Kotlin compiler by reflection to make sure we use the class defined
         * in the kotlin-embeddable jar file. At the time of this writing, the dokka fatJar
         * also contains the compiler and there are some class incompatibilities in it, so
         * this call blows up with a NoClassDefFound in ClassReader if it's the compiler
         * in the dokka jar that gets invoked.
         *
         * There are plenty of ways in which this method can break but this will be immediately
         * apparent if it happens.
         */
//        private fun invokeCompilerWithStringArgs(projectName: String, cp: List<File>, args: List<String>): TaskResult {
//            val allArgs = listOf("-module-name", "project-" + projectName) + args
//            kobaltLog(2, "Calling kotlinc " + allArgs.joinToString(" "))
//
//            //
//            // In order to capture the error stream, I need to invoke CLICompiler.exec(), which
//            // is the first method that accepts a PrintStream for the errors in parameter
//            //
//            val result =
//                ByteArrayOutputStream().use { baos ->
//                    val compilerJar = listOf(kotlinJarFiles.compiler.toURI().toURL())
//
//                    val classLoader = ParentLastClassLoader(compilerJar)
//                    val compiler = classLoader.loadClass("org.jetbrains.kotlin.cli.common.CLICompiler")
//                    val kCompiler = classLoader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
//
//                    PrintStream(baos).use { ps ->
//                        val execMethod = compiler.declaredMethods.filter {
//                            it.name == "exec" && it.parameterTypes.size == 2
//                        }[0]
//                        val exitCode = execMethod.invoke(kCompiler.newInstance(), ps, allArgs.toTypedArray())
//                        val errorString = baos.toString(Charset.defaultCharset().toString())
//
//                        // The return value is an enum
//                        val nameMethod = exitCode.javaClass.getMethod("name")
//                        val success = "OK" == nameMethod.invoke(exitCode).toString()
//                        TaskResult(success, errorString)
//                    }
//                }
//
//            return result
//        }

        /**
         * Reorder the files so that the kotlin-*jar files are at the front.
         */
        private fun compilerFirst(list: List<File>): List<File> {
            val result = arrayListOf<File>()
            list.forEach {
                if (it.name.startsWith("kotlin-")) result.add(0, it)
                else result.add(it)
            }
            return result
        }
    }

    private fun kotlinConfig(project: Project?)
            = (Kobalt.findPlugin(KotlinPlugin.PLUGIN_NAME) as KotlinPlugin).configurationFor(project)

    private fun kotlinVersion(project: Project?)
            = kotlinConfig(project)?.version ?: settings.kobaltCompilerVersion ?: Constants.KOTLIN_COMPILER_VERSION

    private fun compilerDep(version: String)
            = dependencyManager.create("org.jetbrains" + ".kotlin:kotlin-compiler-embeddable:$version")

    fun compilerEmbeddableDependencies(project: Project?, version: String): List<IClasspathDependency> {
        val deps = dependencyManager.transitiveClosure(listOf(compilerDep(version)), requiredBy = project?.name ?: "")
        return deps
    }

    /**
     * Create an ICompilerAction based on the parameters and send it to JvmCompiler.doCompile().
     * TODO: This needs to be removed because it doesn't use contributors. Call
     * JvmCompilerPlugin#createCompilerActionInfo instead
     */
    fun compile(project: Project?, context: KobaltContext?, compileDependencies: List<IClasspathDependency>,
            otherClasspath: List<String>, sourceFiles: List<String>, outputDir: File, args: List<String>,
            compilerSeparateProcess: Boolean) : TaskResult {

        val executor = executors.newExecutor("KotlinCompiler", 10)

        // Force a download of the compiler dependencies
        compilerEmbeddableDependencies(project, kotlinVersion(project)).forEach { it.jarFile.get() }

        executor.shutdown()

        //        val classpathList = arrayListOf(
        //                getKotlinCompilerJar("kotlin-stdlib"),
        //                getKotlinCompilerJar("kotlin-compiler-embeddable"))
        //            .map { FileDependency(it) }

        val dependencies = compileDependencies + otherClasspath.map { FileDependency(it) }

        // The friendPaths is a private setting for the Kotlin compiler that enables a compilation unit
        // to see internal symbols from another compilation unit. By default, set it to kobaltBuild/classes
        // so that tests can see internal from the main code
        val friendPaths =
            if (project != null) {
                listOf(KFiles.joinDir(project.directory, project.buildDirectory, KFiles.CLASSES_DIR))
            } else {
                emptyList<String>()
            }
        val info = CompilerActionInfo(project?.directory, dependencies, sourceFiles, listOf("kt"), outputDir, args,
                friendPaths, context?.internalContext?.forceRecompile ?: false, compilerSeparateProcess)

        return jvmCompiler.doCompile(project, context, compilerAction, info,
                if (context != null) compilerUtils.sourceCompilerFlags(project, context, info) else emptyList())
    }
}

class KConfiguration @Inject constructor(val compiler: KotlinCompiler){
    private val classpath = arrayListOf<String>()
    var dependencies = arrayListOf<IClasspathDependency>()
    var source = arrayListOf<String>()
    var output: File by Delegates.notNull()
    val args = arrayListOf<String>()
    var noIncrementalKotlin = false
    var compilerSeparateProcess = false

    fun sourceFiles(s: String) = source.add(s)

    fun sourceFiles(s: List<String>) = source.addAll(s)

    fun classpath(s: String) = classpath.add(s)

    fun classpath(s: List<String>) = classpath.addAll(s)

    fun compilerArgs(s: List<String>) = args.addAll(s)

    fun compile(project: Project? = null, context: KobaltContext? = null) : TaskResult {
        val saved = context?.internalContext?.noIncrementalKotlin ?: false
        if (context != null) context.internalContext.noIncrementalKotlin = noIncrementalKotlin
        val result = compiler.compile(project, context, dependencies, classpath, source, output, args,
                compilerSeparateProcess)
        if (context != null) context.internalContext.noIncrementalKotlin = saved
        return result
    }
}

fun kotlinCompilePrivate(ini: KConfiguration.() -> Unit) : KConfiguration {
    val result = Kobalt.INJECTOR.getInstance(KConfiguration::class.java)
    result.ini()
    return result
}
