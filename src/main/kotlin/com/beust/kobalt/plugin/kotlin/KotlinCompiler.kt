package com.beust.kobalt.plugin.kotlin;

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.KobaltLogger
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
class KotlinCompiler @Inject constructor(override val localRepo : LocalRepo,
        override val files: com.beust.kobalt.misc.KFiles,
        override val depFactory: DepFactory,
        override val dependencyManager: DependencyManager,
        override val executors: KobaltExecutors)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors), KobaltLogger {
    private val KOTLIN_VERSION = "0.14.449"

    override val name = "kotlin"

    private fun getKotlinCompilerJar(name: String) : String {
        val id = "org.jetbrains.kotlin:$name:$KOTLIN_VERSION"
        val dep = MavenDependency.create(id, executors.miscExecutor)
        val result = dep.jarFile.get().absolutePath
        return result
    }

    fun compile(compileDependencies: List<IClasspathDependency>, otherClasspath: List<String>,
            source: List<String>, output: String, args: List<String>) : TaskResult {
        val executor = executors.newExecutor("KotlinCompiler", 10)
        val compilerDep = depFactory.create("org.jetbrains.kotlin:kotlin-compiler-embeddable:${KOTLIN_VERSION}",
                executor)
        val deps = compilerDep.transitiveDependencies(executor)
        deps.forEach { it.jarFile.get() }

        val classpathList = arrayListOf(
                getKotlinCompilerJar("kotlin-stdlib"),
                getKotlinCompilerJar("kotlin-compiler-embeddable"))

        classpathList.addAll(otherClasspath)
        classpathList.addAll(calculateClasspath(compileDependencies).map { it.id })

        validateClasspath(classpathList)

        log(2, "Compiling ${source.size()} files with classpath:\n  " + classpathList.join("\n  "))
        K2JVMCompiler.main(arrayOf(
                "-d", output,
                "-classpath", classpathList.join(File.pathSeparator), *source.toTypedArray(),
                *args.toTypedArray()))
        executor.shutdown()
        return TaskResult()
    }
}

class KConfiguration @Inject constructor(val compiler: KotlinCompiler){
    val classpath = arrayListOf<String>()
    val dependencies = arrayListOf<IClasspathDependency>()
    var source = arrayListOf<String>()
    var output: String by Delegates.notNull()
    val args = arrayListOf<String>()

    fun sourceFiles(s: String) = source.add(s)

    fun sourceFiles(s: List<String>) = source.addAll(s)

    fun classpath(s: String) = classpath.add(s)

    fun classpath(s: List<String>) = classpath.addAll(s)

    fun compilerArgs(s: List<String>) = args.addAll(s)

    public fun compile() : TaskResult {
        return compiler.compile(dependencies, classpath, source, output, args)
    }
}

fun kotlinCompilePrivate(ini: KConfiguration.() -> Unit) : KConfiguration {
    val result = Kobalt.INJECTOR.getInstance(KConfiguration::class.java)
    result.ini()
    return result
}
