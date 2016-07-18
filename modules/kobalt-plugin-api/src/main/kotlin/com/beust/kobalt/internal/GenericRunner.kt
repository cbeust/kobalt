package com.beust.kobalt.internal

import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.util.*

/**
 * Base class for testing frameworks that are invoked from a main class with arguments. Test runners can
 * subclass this class and override mainClass, args and the name of the dependency that should trigger this runner.
 */
abstract class GenericTestRunner: ITestRunnerContributor {
    abstract val dependencyName : String
    abstract val mainClass: String
    abstract val annotationPackage: String
    abstract fun args(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            testConfig: TestConfig) : List<String>

    override fun run(project: Project, context: KobaltContext, configName: String,
            classpath: List<IClasspathDependency>)
        = TaskResult(runTests(project, context, classpath, configName))

    override fun affinity(project: Project, context: KobaltContext) =
            if (project.testDependencies.any { it.id.contains(dependencyName)}) IAffinity.DEFAULT_POSITIVE_AFFINITY
            else 0

    protected fun findTestClasses(project: Project, context: KobaltContext, testConfig: TestConfig): List<String> {
        val testClassDir = KFiles.joinDir(project.buildDirectory, KFiles.TEST_CLASSES_DIR)
        val path = testClassDir.apply {
            File(this).mkdirs()
        }

        val files = IFileSpec.GlobSpec(toClassPaths(testConfig.testIncludes))
                .toFiles(project.directory, path, testConfig.testExcludes.map { Glob(it) })
        val testClasses = files
            .map {
                File(KFiles.joinDir(project.directory, testClassDir, it.path))
            }
        val result = testClasses.map {
            val prefix = KFiles.joinDir(project.directory, testClassDir)
            val className = it.toString().substring(prefix.length + 1)
                    .replace("/", ".").replace("\\", ".").replace(".class", "")
            Pair(it, className)
        }
//            .filter {
//                val result = acceptClass(it.first, it.second, testClasspath, File(testClassDir))
//                result
//            }

        log(2, "Found ${result.size} test classes")
        return result.map { it.second }
    }

    /**
     * Accept the given class if it contains an annotation of the current test runner's package. Annotations
     * are looked up on both the classes and methods.
     */
//    private fun acceptClass(cf: File, className: String, testClasspath: List<IClasspathDependency>,
//            testClassDir: File): Boolean {
//        val cp = (testClasspath.map { it.jarFile.get() } + listOf(testClassDir)).map { it.toURI().toURL() }
//        try {
//            val cls = URLClassLoader(cp.toTypedArray()).loadClass(className)
//            val ann = cls.annotations.filter {
//                val qn = it.annotationClass.qualifiedName
//                qn != null && qn.contains(annotationPackage)
//            }
//            if (ann.any()) {
//                return true
//            } else {
//                val ann2 = cls.declaredMethods.flatMap { it.declaredAnnotations.toList() }.filter { it.toString()
//                        .contains(annotationPackage)}
//                if (ann2.any()) {
//                    val a0 = ann2[0]
//                    return true
//                }
//            }
//        } catch(ex: Throwable) {
//            println("Exception: " + ex.message)
//            return false
//        }
//        return false
//    }

    private fun toClassPaths(paths: List<String>): ArrayList<String> =
            paths.map { if (it.endsWith("class")) it else it + "class" }.toCollection(ArrayList())

    /**
     * @return true if all the tests passed
     */
    fun runTests(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            configName: String) : Boolean {
        var result = false

        val testConfig = project.testConfigs.firstOrNull { it.name == configName }

        if (testConfig != null) {
            val args = args(project, context, classpath, testConfig)
            if (args.size > 0) {

                val java = JavaInfo.create(File(SystemProperties.javaBase)).javaExecutable
                val jvmArgs = calculateAllJvmArgs(project, context, testConfig, classpath,
                        Kobalt.INJECTOR.getInstance (PluginInfo::class.java))
                val allArgs = arrayListOf<String>().apply {
                    add(java!!.absolutePath)
                    addAll(jvmArgs)
                    add(mainClass)
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

    /*
     ** @return all the JVM flags from contributors and interceptors.
     */
    @VisibleForTesting
    fun calculateAllJvmArgs(project: Project, context: KobaltContext,
            testConfig: TestConfig, classpath: List<IClasspathDependency>, pluginInfo: IPluginInfo) : List<String> {
        // Default JVM args
        val jvmFlags = arrayListOf<String>().apply {
            addAll(testConfig.jvmArgs)
            add("-classpath")
            add(classpath.map { it.jarFile.get().absolutePath }.joinToString(File.pathSeparator))
        }

        // JVM flags from the contributors
        val jvmFlagsFromContributors = pluginInfo.testJvmFlagContributors.flatMap {
            it.testJvmFlagsFor(project, context, jvmFlags)
        }

        // JVM flags from the interceptors (these overwrite flags instead of just adding to the list)
        var result = ArrayList(jvmFlags + jvmFlagsFromContributors)
        pluginInfo.testJvmFlagInterceptors.forEach {
            val newFlags = it.testJvmFlagsFor(project, context, result)
            result.clear()
            result.addAll(newFlags)
        }

        if (result.any()) {
            log(2, "Final JVM test flags after running the contributors and interceptors: $result")
        }

        return result
    }

}

