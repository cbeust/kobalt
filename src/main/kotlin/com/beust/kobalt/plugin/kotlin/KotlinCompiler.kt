package com.beust.kobalt.plugin.kotlin;

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.CompilerActionInfo
import com.beust.kobalt.internal.ICompilerAction
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.TaskResult
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.FileDependency
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.log
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.properties.Delegates

/**
 * @author Cedric Beust <cedric@beust.com>
 * @since 08 03, 2015
 */
@Singleton
class KotlinCompiler @Inject constructor(val localRepo : LocalRepo,
        val files: com.beust.kobalt.misc.KFiles,
        val depFactory: DepFactory,
        val executors: KobaltExecutors,
        val jvmCompiler: JvmCompiler) {
    companion object {
        val KOTLIN_VERSION = "1.0.0-beta-1103"
    }

    val compilerAction = object: ICompilerAction {
        override fun compile(info: CompilerActionInfo): TaskResult {
            if (info.sourceFiles.size > 1) {
                log(1, "  Compiling ${info.sourceFiles.size} files")
            }
            val allArgs : Array<String> = arrayOf(
                    "-d", info.outputDir.path,
                    "-classpath", info.dependencies.map {it.jarFile.get()}.joinToString(File.pathSeparator),
                    *(info.compilerArgs.toTypedArray()),
                    *(info.sourceFiles.toTypedArray())
            )
            log(2, "Calling kotlinc " + allArgs.joinToString(" "))
            CLICompiler.doMainNoExit(K2JVMCompiler(), allArgs)
            return TaskResult()
        }
    }

    /**
     * Create an ICompilerAction based on the parameters and send it to JvmCompiler.doCompile().
     */
    fun compile(project: Project?, context: KobaltContext?, compileDependencies: List<IClasspathDependency>,
            otherClasspath: List<String>, sourceFiles: List<String>, outputDir: File, args: List<String>) : TaskResult {

        val executor = executors.newExecutor("KotlinCompiler", 10)
        val compilerDep = depFactory.create("org.jetbrains.kotlin:kotlin-compiler-embeddable:$KOTLIN_VERSION", executor)
        val deps = compilerDep.transitiveDependencies(executor)

        // Force a download of the compiler dependencies
        deps.forEach { it.jarFile.get() }

        executor.shutdown()

//        val classpathList = arrayListOf(
//                getKotlinCompilerJar("kotlin-stdlib"),
//                getKotlinCompilerJar("kotlin-compiler-embeddable"))
//            .map { FileDependency(it) }

        val dependencies = compileDependencies + otherClasspath.map { FileDependency(it)}
        val info = CompilerActionInfo(project?.directory, dependencies, sourceFiles, outputDir, args)
        return jvmCompiler.doCompile(project, context, compilerAction, info)
    }
}

class KConfiguration @Inject constructor(val compiler: KotlinCompiler){
    val classpath = arrayListOf<String>()
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
        return compiler.compile(project, context, dependencies, classpath, source, output, args + "-no-stdlib")
    }
}

fun kotlinCompilePrivate(ini: KConfiguration.() -> Unit) : KConfiguration {
    val result = Kobalt.INJECTOR.getInstance(KConfiguration::class.java)
    result.ini()
    return result
}
