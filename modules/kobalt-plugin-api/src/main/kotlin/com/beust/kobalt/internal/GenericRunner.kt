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
abstract class GenericTestRunner : ITestRunnerContributor {
    abstract val dependencyName : String
    abstract val mainClass: String
    abstract fun args(project: Project, classpath: List<IClasspathDependency>, testConfig: TestConfig) : List<String>

    override fun run(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>)
            = TaskResult(runTests(project, classpath))

    override fun affinity(project: Project, context: KobaltContext) =
            if (project.testDependencies.any { it.id.contains(dependencyName)}) IAffinity.DEFAULT_POSITIVE_AFFINITY
            else 0

    protected fun findTestClasses(project: Project, testConfig: TestConfig): List<String> {
        val path = KFiles.joinDir(project.directory, project.buildDirectory, KFiles.TEST_CLASSES_DIR)

        val result = IFileSpec.GlobSpec(toClassPaths(testConfig.testIncludes))
            .toFiles(path, testConfig.testExcludes.map {
                    Glob(it)
                }).map {
                    it.toString().replace("/", ".").replace("\\", ".").replace(".class", "")
                }

        log(2, "Found ${result.size} test classes")
        return result
    }

    private fun toClassPaths(paths: List<String>): ArrayList<String> =
            paths.map { if (it.endsWith("class")) it else it + "class" }.toArrayList()

    /**
     * @return true if all the tests passed
     */
    fun runTests(project: Project, classpath: List<IClasspathDependency>) : Boolean {
        val jvm = JavaInfo.create(File(SystemProperties.javaBase))
        val java = jvm.javaExecutable
        var result = false

        project.testConfigs.forEach { testConfig ->
            val args = args(project, classpath, testConfig)
            if (args.size > 0) {
                val allArgs = arrayListOf<String>().apply {
                    add(java!!.absolutePath)
                    addAll(testConfig.jvmArgs)
                    add("-classpath")
                    add(classpath.map { it.jarFile.get().absolutePath }.joinToString(File.pathSeparator))
                    add(mainClass)
                    addAll(args)
                }

                val pb = ProcessBuilder(allArgs)
                pb.directory(File(project.directory))
                pb.inheritIO()
                log(1, "Running tests with classpath size ${classpath.size}")
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
        }
        return result
    }
}

