package com.beust.kobalt.internal

import com.beust.kobalt.AsciiArt
import com.beust.kobalt.TestConfig
import com.beust.kobalt.TestResult
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.aether.AetherDependency
import com.beust.kobalt.misc.*
import org.testng.remote.RemoteArgs
import org.testng.remote.strprotocol.JsonMessageSender
import org.testng.remote.strprotocol.MessageHelper
import org.testng.remote.strprotocol.MessageHub
import org.testng.remote.strprotocol.TestResultMessage
import org.w3c.dom.Attr
import org.xml.sax.InputSource
import java.io.File
import java.io.FileReader
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

class TestNgRunner : GenericTestRunner() {

    override val mainClass = "org.testng.TestNG"
    override val dependencyName = "testng"
    override val annotationPackage = "org.testng"
    override val runnerName = "TestNG"

    fun defaultOutput(project: Project) = KFiles.joinDir(project.buildDirectory, "test-output")

    override fun args(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            testConfig: TestConfig) = arrayListOf<String>().apply {

        if (KobaltLogger.isQuiet) {
            add("-log")
            add("0")
        }

        if (testConfig.testArgs.none { it == "-d" }) {
            add("-d")
            add(defaultOutput(project))
        }

        if (testConfig.testArgs.size == 0) {
            // No arguments, so we'll do it ourselves. Either testng.xml or the list of classes
            val testngXml = File(project.directory, KFiles.joinDir("src", "test", "resources", "testng.xml"))
            if (testngXml.exists()) {
                add(testngXml.absolutePath)
            } else {
                val testClasses = findTestClasses(project, context, testConfig)
                if (testClasses.isNotEmpty()) {
                    addAll(testConfig.testArgs)

                    add("-testclass")
                    add(testClasses.joinToString(","))
                } else {
                    if (!testConfig.isDefault) warn("Couldn't find any test classes for ${project.name}")
                    // else do nothing: since the user didn't specify an explicit test{} directive, not finding
                    // any test sources is not a problem
                }
            }
        } else {
            addAll(testConfig.testArgs)
        }
    }

    /**
     * Extract test results from testng-results.xml and initialize shortMessage.
     */
    override fun onFinish(project: Project) {
        File(defaultOutput(project), "testng-results.xml").let { file ->
            val ins = InputSource(FileReader(file))
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ins)

            val root = doc.documentElement
            var failed = 0
            var skipped = 0
            var passed = 0
            repeat(root.attributes.length) {
                val attribute = root.attributes.item(it)
                if (attribute is Attr) when (attribute.name) {
                    "failed" -> failed = Integer.parseInt(attribute.value)
                    "skipped" -> skipped = Integer.parseInt(attribute.value)
                    "passed" -> passed = Integer.parseInt(attribute.value)
                }
            }

            if (failed == 0) {
                shortMessage = "$passed tests"
            } else if (failed > 0) {
                shortMessage = "$failed failed" + (if (skipped > 0) ", $skipped skipped" else "") + " tests"
            }
        }
    }

    val VERSION_6_10 = StringVersion("6.10")

    fun _runTests(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
//    override fun runTests(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            configName: String): TestResult {

        val testConfig = project.testConfigs.firstOrNull { it.name == configName }

        if (testConfig != null) {
            context.logger.log(project.name, 1, "Running enhanced TestNG runner")

            val testngDependency = (project.testDependencies.filter { it.id.contains("testng") }
                    .firstOrNull() as AetherDependency).version
            val versions = findRemoteRunnerVersion(testngDependency)
            val useOldRunner = System.getProperty("testng.oldRunner") != null
            val result =
                if (versions != null && ! useOldRunner) {
                    context.logger.log(project.name, 1, "Modern TestNG, displaying colors")
                    displayPrettyColors(project, context, classpath, testConfig, versions)
                } else {
                    context.logger.log(project.name, 1, "Older TestNG ($testngDependency), using the old runner")
                    super.runTests(project, context, classpath, configName)
                }
            return result
        } else {
            return TestResult(true)
        }
    }

    private fun findRemoteRunnerVersion(testngVersion: String) : Pair<String, String>? {
        val tng = StringVersion(testngVersion)
        val result =
            if (tng >= VERSION_6_10) Pair(testngVersion, "testng-remote6_10")
            else if (tng >= StringVersion("6.9.10")) Pair("6.9.10", "testng-remote6_9_10")
            else if (tng >= StringVersion("6.9.7")) Pair("6.9.7", "testng-remote6_9_7")
            else if (tng >= StringVersion("6.5.1")) Pair("6.5.1", "testng-remote6_5_0")
            else if (tng >= StringVersion("6.0")) Pair("6.0", "testng-remote6_0")
            else null
        return result
    }

    private fun displayPrettyColors(project: Project, context: KobaltContext,
            classpath: List<IClasspathDependency>, testConfig: TestConfig, versions: Pair<String, String>)
            : TestResult {
        val port = 2345
//        launchRemoteServer(project, context, classpath, testConfig, versions, port)

        val mh = MessageHub(JsonMessageSender("localhost", port, true))
        mh.setDebug(true)
        mh.initReceiver()
        val passed = arrayListOf<String>()

        data class FailedTest(val method: String, val cls: String, val stackTrace: String)

        val failed = arrayListOf<FailedTest>()
        val skipped = arrayListOf<String>()

        fun d(n: Int, color: String)
                = AsciiArt.wrap(String.format("%4d", n), color)

        fun red(s: String) = AsciiArt.wrap(s, AsciiArt.RED)
        fun green(s: String) = AsciiArt.wrap(s, AsciiArt.GREEN)
        fun yellow(s: String) = AsciiArt.wrap(s, AsciiArt.YELLOW)

        try {
            var message = mh.receiveMessage()
            kobaltLog(1, "")
            kobaltLog(1, green("PASSED") + " | " + red("FAILED") + " | " + yellow("SKIPPED"))
            while (message != null) {
                message = mh.receiveMessage()
                if (message is TestResultMessage) {
                    when (message.result) {
                        MessageHelper.PASSED_TEST -> passed.add(message.name)
                        MessageHelper.FAILED_TEST -> failed.add(FailedTest(message.testClass,
                                message.method, message.stackTrace))
                        MessageHelper.SKIPPED_TEST -> skipped.add(message.name)
                    }
                }
                if (!KobaltLogger.isQuiet) {
                    print("\r  " + d(passed.size, AsciiArt.GREEN)
                            + " |   " + d(failed.size, AsciiArt.RED)
                            + " |   " + d(skipped.size, AsciiArt.YELLOW))
                }
            }
        } catch(ex: IOException) {
             kobaltLog(1, "Exception: ${ex.message}")
        }
        kobaltLog(1, "\nPassed: " + passed.size + ", Failed: " + failed.size + ", Skipped: " + skipped.size)
        failed.forEach {
            val top = it.stackTrace.substring(0, it.stackTrace.indexOf("\n"))
             kobaltLog(1, "  " + it.cls + "." + it.method + "\n    " + top)
        }
        return TestResult(failed.isEmpty() && skipped.isEmpty())
    }

    fun launchRemoteServer(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            testConfig: TestConfig, versions: Pair<String, String>, port: Int) {
        val testngVersion = versions.first
        val remoteRunnerVersion = versions.second
        val dep = with(context.dependencyManager) {
            val jf = create("org.testng.testng-remote:testng-remote:1.3.0")
            val tr = create("org.testng.testng-remote:$remoteRunnerVersion:1.3.0")
            val testng = create("org.testng:testng:6.11")
            transitiveClosure(kotlin.collections.listOf(jf, tr /*, testng */))
        }

        val cp = (classpath + dep).distinct().map { it.jarFile.get() }
                .joinToString(File.pathSeparator)
        val calculatedArgs = args(project, context, classpath, testConfig)

        val jvmArgs = arrayListOf("-classpath", cp)
        if (testConfig.jvmArgs.any()) {
            jvmArgs.addAll(testConfig.jvmArgs)
        }
        val remoteArgs = listOf(
                "org.testng.remote.RemoteTestNG",
                "-serport", port.toString(),
                "-version", testngVersion,
                "-dontexit",
                RemoteArgs.PROTOCOL,
                "json")

        val passedArgs = jvmArgs +  remoteArgs + calculatedArgs

        Thread {
            runCommand {
                command = "java"
                directory = File(project.directory)
                args = passedArgs
            }
        }.start()

//        Thread {
//            val args2 = arrayOf("-serport", port.toString(), "-dontexit", RemoteArgs.PROTOCOL, "json",
//                    "-version", "6.10",
//                    "src/test/resources/testng.xml")
//            RemoteTestNG.main(args2)
//        }.start()
    }
}

fun main(args: Array<String>) {
    fun d(n: Int, color: String)
            = AsciiArt.wrap(String.format("%4d", n), color)

    if (!KobaltLogger.isQuiet) {
        println("PASSED | FAILED | SKIPPED")
        repeat(20) { i ->
            print("\r  " + d(i, AsciiArt.GREEN) + " |   " + d(i * 2, AsciiArt.RED) + " | " + d(i, AsciiArt.YELLOW))
            Thread.sleep(500)
        }
        println("")
    }
}
