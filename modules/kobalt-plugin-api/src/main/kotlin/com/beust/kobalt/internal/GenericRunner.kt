package com.beust.kobalt.internal

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.util.*

/**
 * Base class for testing frameworks that are invoked from a main class with arguments. Test runners can
 * subclass this class and override mainClass, args and the name of the dependency that should trigger this runner.
 */
abstract class GenericTestRunner: ITestRunnerContributor {
    abstract val dependencyName : String
    abstract val mainClass: String
    abstract fun args(project: Project, classpath: List<IClasspathDependency>, testConfig: TestConfig) : List<String>

    override fun run(project: Project, context: KobaltContext, configName: String,
            classpath: List<IClasspathDependency>)
        = TaskResult(runTests(project, context, classpath, configName))

    override fun affinity(project: Project, context: KobaltContext) =
            if (project.testDependencies.any { it.id.contains(dependencyName)}) IAffinity.DEFAULT_POSITIVE_AFFINITY
            else 0

    protected fun findTestClasses(project: Project, testConfig: TestConfig): List<String> {
        val path = KFiles.joinDir(project.buildDirectory, KFiles.TEST_CLASSES_DIR).apply {
            File(this).mkdirs()
        }

        val result = IFileSpec.GlobSpec(toClassPaths(testConfig.testIncludes))
            .toFiles(project.directory, path, testConfig.testExcludes.map {
                    Glob(it)
                }).map {
                    it.toString().replace("/", ".").replace("\\", ".").replace(".class", "")
                }

        log(2, "Found ${result.size} test classes")
        return result
    }

    private fun toClassPaths(paths: List<String>): ArrayList<String> =
            paths.map { if (it.endsWith("class")) it else it + "class" }.toCollection(ArrayList())

    /**
     * @return true if all the tests passed
     */
    fun runTests(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            configName: String) : Boolean {
        val jvm = JavaInfo.create(File(SystemProperties.javaBase))
        val java = jvm.javaExecutable
        var result = false

        val testConfig = project.testConfigs.firstOrNull { it.name == configName }

        if (testConfig != null) {
            val args = args(project, classpath, testConfig)
            if (args.size > 0) {

                // Default JVM args
                val jvmFlags = arrayListOf<String>().apply {
                    addAll(testConfig.jvmArgs)
                    add("-classpath")
                    add(classpath.map { it.jarFile.get().absolutePath }.joinToString(File.pathSeparator))
                    add(mainClass)
                }

                val pluginInfo = Kobalt.INJECTOR.getInstance(PluginInfo::class.java)

                // JVM flags from the contributors
                val flagsFromContributors = pluginInfo.testJvmFlagContributors.flatMap {
                    it.testJvmFlagsFor(project, context, jvmFlags)
                }

                // JVM flags from the interceptors (these overwrite flags instead of just adding to the list)
                var interceptedArgs = ArrayList(flagsFromContributors)
                pluginInfo.testJvmFlagInterceptors.forEach {
                    val newFlags = it.testJvmFlagsFor(project, context, interceptedArgs)
                    interceptedArgs.clear()
                    interceptedArgs.addAll(newFlags)
                }

                if (interceptedArgs.any()) {
                    log(2, "Final JVM test flags after running the contributors and interceptors: $interceptedArgs")
                }

                val allArgs = arrayListOf<String>().apply {
                    add(java!!.absolutePath)
                    addAll(interceptedArgs)
                    addAll(args)
                }

                val pb = ProcessBuilder(allArgs)
                pb.directory(File(project.directory))
                pb.inheritIO()
                log(2, "Running tests with classpath size ${classpath.size}")
                log(2, "Launching " + allArgs.joinToString(" "))
                val process = pb.start()
                val errorCode = process.waitFor()
                if (errorCode == 0) {
                    log(1, "All tests passed")
                } else {
                    log(1, "Test failures")
                }
                result = result || errorCode == 0
            } else {
                log(2, "Couldn't find any test classes")
                result = true
            }
        } else {
            throw KobaltException("Couldn't find a test configuration named \"$configName\"")
        }
        return result
    }
}

