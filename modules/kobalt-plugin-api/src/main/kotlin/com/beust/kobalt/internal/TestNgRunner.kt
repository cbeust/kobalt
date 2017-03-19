package com.beust.kobalt.internal

import com.beust.kobalt.AsciiArt
import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.homeDir
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.runCommand
import com.beust.kobalt.misc.warn
import org.testng.remote.RemoteArgs
import org.testng.remote.strprotocol.JsonMessageSender
import org.testng.remote.strprotocol.MessageHelper
import org.testng.remote.strprotocol.MessageHub
import org.testng.remote.strprotocol.TestResultMessage
import java.io.File
import java.io.IOException

class TestNgRunner : GenericTestRunner() {

    override val mainClass = "org.testng.TestNG"

    override val dependencyName = "testng"

    override val annotationPackage = "org.testng"

    fun defaultOutput(project: Project) = KFiles.joinDir(KFiles.KOBALT_BUILD_DIR, project.buildDirectory, "test-output")

    override fun args(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            testConfig: TestConfig) = arrayListOf<String>().apply {

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
                    if (! testConfig.isDefault) warn("Couldn't find any test classes for ${project.name}")
                    // else do nothing: since the user didn't specify an explicit test{} directive, not finding
                    // any test sources is not a problem
                }
            }
        } else {
            addAll(testConfig.testArgs)
        }
    }

    fun _runTests(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            configName: String): Boolean {
        var result = false
        val port = 2345

        val classpath = listOf(homeDir("java/jcommander/kobaltBuild/classes"),
                homeDir(".kobalt/cache/org/testng/testng/6.10/testng-6.10.jar"),
                homeDir(".kobalt/cache/com/beust/jcommander/1.66/jcommander-1.66.jar"),
                homeDir(".kobalt/cache/org/yaml/snakeyaml/1.17/snakeyaml-1.17.jar"),
                homeDir(".kobalt/cache/com/google/code/findbugs/jsr305/3.0.1/jsr305-3.0.1.jar"),
                homeDir("java/jcommander/kobaltBuild/test-classes"),
                homeDir("java/jcommander/src/test/resources/testng.xml"),
                homeDir("kotlin/kobalt/lib/testng-remote-1.3.0-SNAPSHOT.jar"),
                homeDir("kotlin/kobalt/lib/testng-remote6_10-1.3.0-SNAPSHOT.jar")
            ).joinToString(File.pathSeparator)
        val passedArgs = listOf(
                "-classpath",
                classpath,
                "org.testng.remote.RemoteTestNG",
                "-serport", port.toString(),
                "-version", "6.10",
                "-dontexit",
                RemoteArgs.PROTOCOL,
                "json",
                "src/test/resources/testng.xml")

        Thread {
            val exitCode = runCommand {
                command = "java"
                directory = File(homeDir("java/jcommander"))
                args = passedArgs
            }
        }.start()

//        Thread {
//            val args2 = arrayOf("-serport", port.toString(), "-dontexit", RemoteArgs.PROTOCOL, "json",
//                    "-version", "6.10",
//                    "src/test/resources/testng.xml")
//            RemoteTestNG.main(args2)
//        }.start()

        val mh = MessageHub(JsonMessageSender("localhost", port, true))
        mh.setDebug(true)
        mh.initReceiver()
        val passed = arrayListOf<String>()
        data class FailedTest(val method: String, val cls: String, val stackTrace: String)
        val failed = arrayListOf<FailedTest>()
        var skipped = arrayListOf<String>()
        try {
            var message = mh.receiveMessage()
            println("")
            while (message != null) {
                message = mh.receiveMessage()
                if (message is TestResultMessage) {
                    when(message.result) {
                        MessageHelper.PASSED_TEST -> passed.add(message.name)
                        MessageHelper.FAILED_TEST -> failed.add(FailedTest(message.testClass,
                                message.method, message.stackTrace))
                        MessageHelper.SKIPPED_TEST -> skipped.add(message.name)
                    }
                }
                print("\r" + String.format("%4d / %4d / %4d", passed.size, failed.size, skipped.size))
//                Thread.sleep(200)
            }
        } catch(ex: IOException) {
            println("Exception: ${ex.message}")
        }
        println("\nPassed: " + passed.size + ", Failed: " + failed.size + ", Skipped: " + skipped.size)
        failed.forEach {
            val top = it.stackTrace.substring(0, it.stackTrace.indexOf("\n"))
            println("  " + it.cls + "." + it.method + "\n    " + top)
        }
        return result
    }
}

fun main(args: Array<String>) {
    fun d(n: Int, color: String)
            = AsciiArt.wrap(String.format("%4d", n), color)

    println("PASSED | FAILED | SKIPPED")
    repeat(20) { i ->
        print("\r  " + d(i, AsciiArt.GREEN) + " |   " + d(i * 2, AsciiArt.RED) + " | " + d(i, AsciiArt.YELLOW))
        Thread.sleep(500)
    }
    println("")
}
