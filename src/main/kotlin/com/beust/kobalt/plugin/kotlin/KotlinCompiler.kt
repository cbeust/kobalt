package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.internal.ICompilerAction
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.KotlinJarFiles
import com.beust.kobalt.kotlin.ParentLastClassLoader
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.log
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.Charset
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
        val dependencyManager: DependencyManager,
        val executors: KobaltExecutors,
        val settings: KobaltSettings,
        val jvmCompiler: JvmCompiler,
        val kotlinJarFiles: KotlinJarFiles) {

    val compilerAction = object: ICompilerAction {
        override fun compile(projectName: String?, info: CompilerActionInfo): TaskResult {
            val version = settings.kobaltCompilerVersion
            if (! info.outputDir.path.endsWith("ript.jar")) {
                // Don't display the message if compiling Build.kt
                log(1, "  Kotlin $version compiling " + Strings.pluralizeAll(info.sourceFiles.size, "file"))
            }
            val cp = compilerFirst(info.dependencies.map {it.jarFile.get()})
            val infoDir = info.directory
            val outputDir = if (infoDir != null) {
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

//            return invokeCompilerWithCompilerArgs(projectName ?: "kobalt-" + Random().nextInt(), outputDir, classpath,
//                    info.sourceFiles)
            return invokeCompilerWithStringArgs(projectName ?: "kobalt-" + Random().nextInt(), cp, allArgs)
        }

        private fun invokeCompilerWithCompilerArgs(projectName: String, outputDir: String?, classpathString: String,
                sourceFiles: List<String>): TaskResult {
            val args = K2JVMCompilerArguments().apply {
                moduleName = projectName
                destination = outputDir
                classpath = classpathString
                freeArgs = sourceFiles
                friendPaths = arrayOf("kobaltBuild\\classes")
            }
            log(1, "Invoking K2JVMCompiler with arguments:"
                    + " -moduleName " + args.moduleName
                    + " -d " + args.destination
                    + " -friendPaths " + args.friendPaths.joinToString(";")
                    + " -classpath " + args.classpath
                    + " " + sourceFiles.joinToString(" "))
            val collector = object : MessageCollector {
                override fun hasErrors(): Boolean {
                    throw UnsupportedOperationException("not implemented")
                }

                fun CompilerMessageLocation.dump(s: String) = "$path:$line:$column $s"

                override fun report(severity: CompilerMessageSeverity,
                        message: String, location: CompilerMessageLocation) {
                    if (severity.isError) {
                        System.err.println(location.dump(message))
                    } else if (severity == CompilerMessageSeverity.WARNING) {
                        warn(location.dump(message))
                    } else if (severity == CompilerMessageSeverity.INFO && KobaltLogger.LOG_LEVEL >= 2) {
                        log(2, location.dump(message))
                    }
                }
            }
            val exitCode = K2JVMCompiler().exec(collector, Services.Builder().build(), args)
            val result = TaskResult(exitCode == ExitCode.OK)
            return result
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
        private fun invokeCompilerWithStringArgs(projectName: String, cp: List<File>, args: List<String>): TaskResult {
            val allArgs = listOf("-module-name", "project-" + projectName) + args
            log(2, "Calling kotlinc " + allArgs.joinToString(" "))

            //
            // In order to capture the error stream, I need to invoke CLICompiler.exec(), which
            // is the first method that accepts a PrintStream for the errors in parameter
            //
            val result =
                ByteArrayOutputStream().use { baos ->
                    val compilerJar = listOf(kotlinJarFiles.compiler.toURI().toURL())

                    val classLoader = ParentLastClassLoader(compilerJar)
                    val compiler = classLoader.loadClass("org.jetbrains.kotlin.cli.common.CLICompiler")
                    val kCompiler = classLoader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")

                    PrintStream(baos).use { ps ->
                        val execMethod = compiler.declaredMethods.filter {
                            it.name == "exec" && it.parameterTypes.size == 2
                        }[0]
                        val exitCode = execMethod.invoke(kCompiler.newInstance(), ps, allArgs.toTypedArray())
                        val errorString = baos.toString(Charset.defaultCharset().toString())

                        // The return value is an enum
                        val nameMethod = exitCode.javaClass.getMethod("name")
                        val success = "OK" == nameMethod.invoke(exitCode).toString()
                        TaskResult(success, errorString)
                    }
                }

            return result
        }

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

    /**
     * Create an ICompilerAction based on the parameters and send it to JvmCompiler.doCompile().
     * TODO: This needs to be removed because it doesn't use contributors. Call
     * JvmCompilerPlugin#createCompilerActionInfo instead
     */
    fun compile(project: Project?, context: KobaltContext?, compileDependencies: List<IClasspathDependency>,
            otherClasspath: List<String>, sourceFiles: List<String>, outputDir: File, args: List<String>) : TaskResult {

        val executor = executors.newExecutor("KotlinCompiler", 10)
        val compilerVersion = settings.kobaltCompilerVersion
        val compilerDep = dependencyManager.create("org.jetbrains.kotlin:kotlin-compiler-embeddable:$compilerVersion")
        val deps = dependencyManager.transitiveClosure(listOf(compilerDep), project?.name ?: "")

        // Force a download of the compiler dependencies
        deps.forEach { it.jarFile.get() }

        executor.shutdown()

        //        val classpathList = arrayListOf(
        //                getKotlinCompilerJar("kotlin-stdlib"),
        //                getKotlinCompilerJar("kotlin-compiler-embeddable"))
        //            .map { FileDependency(it) }

        val dependencies = compileDependencies + otherClasspath.map { FileDependency(it) }
        val info = CompilerActionInfo(project?.directory, dependencies, sourceFiles, listOf("kt"), outputDir, args)
        return jvmCompiler.doCompile(project, context, compilerAction, info)
    }
}

class KConfiguration @Inject constructor(val compiler: KotlinCompiler){
    private val classpath = arrayListOf<String>()
    val dependencies = arrayListOf<IClasspathDependency>()
    var source = arrayListOf<String>()
    var output: File by Delegates.notNull()
    val args = arrayListOf<String>()

    fun sourceFiles(s: String) = source.add(s)

    fun sourceFiles(s: List<String>) = source.addAll(s)

    fun classpath(s: String) = classpath.add(s)

    fun classpath(s: List<String>) = classpath.addAll(s)

    fun compilerArgs(s: List<String>) = args.addAll(s)

    fun compile(project: Project? = null, context: KobaltContext? = null) : TaskResult {
        return compiler.compile(project, context, dependencies, classpath, source, output, args)
    }
}

fun kotlinCompilePrivate(ini: KConfiguration.() -> Unit) : KConfiguration {
    val result = Kobalt.INJECTOR.getInstance(KConfiguration::class.java)
    result.ini()
    return result
}
