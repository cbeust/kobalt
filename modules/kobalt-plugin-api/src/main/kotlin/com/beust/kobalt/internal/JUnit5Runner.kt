package com.beust.kobalt.internal

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.IAffinity
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltLogger
import com.google.inject.Inject
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import java.io.File
import java.nio.file.Paths

/**
 * Runner for JUnit 5 tests. This class also contains a main() entry point since JUnit 5 no longer supplies one.
 */
class JUnit5Runner @Inject constructor(kFiles: KFiles) : GenericTestRunner() {

    override val dependencyName = "jupiter"
    override val annotationPackage = "org.junit.jupiter.api"
    override val mainClass = "com.beust.kobalt.internal.JUnit5RunnerKt"
    override val runnerName = "JUnit 5"

    override fun affinity(project: Project, context: KobaltContext) : Int {
        val result =
                if (project.testDependencies.any { it.id.contains("junit5") || it.id.contains("jupiter") })
                    IAffinity.DEFAULT_POSITIVE_AFFINITY + 100
                else 0
        return result

    }

    override fun args(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>, testConfig: TestConfig): List<String> {
        val testClassDir = KFiles.joinDir(project.buildDirectory, KFiles.TEST_CLASSES_DIR)
        val classDir = KFiles.joinDir(project.buildDirectory, KFiles.CLASSES_DIR)
        val args = listOf("--testClassDir", testClassDir,
                "--classDir", classDir,
                "--log", KobaltLogger.LOG_LEVEL.toString())
        return args
    }

    override val extraClasspath = kFiles.kobaltJar
}

private class Args {
    @Parameter(names = arrayOf("--log"))
    var log: Int = 1

    @Parameter(names = arrayOf("--testClassDir"))
    var testClassDir: String = "kobaltBuild/test-classes"

    @Parameter(names = arrayOf("--classDir"))
    var classDir: String = "kobaltBuild/classes"
}

fun main(argv: Array<String>) {
    val args = Args()
    val jc = JCommander(args)
    jc.parse(*argv)

    val testClassDir = File(args.testClassDir).absolutePath
    val classDir = File(args.classDir).absolutePath
    val request : LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder()
            .selectors(DiscoverySelectors.selectClasspathRoots(setOf(
                    Paths.get(testClassDir),
                    Paths.get(classDir)
            )))
            .selectors(DiscoverySelectors.selectDirectory(testClassDir))
            .build()

    fun testName(id: TestIdentifier) : String? {
        val result =
                if (id.source.isPresent) {
                    val source = id.source.get()
                    if (source is MethodSource) {
                        source.className + "." + source.methodName
                    } else {
                        null
                    }
                } else {
                    null
                }
        return result
    }

    var passed = 0
    var failed = 0
    var skipped = 0
    var aborted = 0

    fun log(level: Int, s: String) {
        if (level <= args.log) println(s)
    }

    val listener = object: TestExecutionListener {
        override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
            val testName = testName(testIdentifier)
            if (testName != null) {
                when(testExecutionResult.status) {
                    TestExecutionResult.Status.FAILED -> {
                        log(1, "FAILED: $testName, reason: " + testExecutionResult.throwable.get().toString())
                        failed++
                    }
                    TestExecutionResult.Status.ABORTED -> {
                        log(1, "ABORTED: $testName, reason: " + testExecutionResult.throwable.get().toString())
                        aborted++
                    }
                    TestExecutionResult.Status.SUCCESSFUL -> {
                        log(2, "PASSED: $testName")
                        passed++
                    } else -> {

                    }
                }
            }
        }

        override fun executionSkipped(testIdentifier: TestIdentifier, reason: String) {
            testName(testIdentifier)?.let {
                log(1, "Skipping $it because $reason")
                skipped++
            }
        }

        override fun executionStarted(testIdentifier: TestIdentifier) {
            testName(testIdentifier)?.let {
                log(2, "Starting $it")
            }
        }

        override fun testPlanExecutionStarted(testPlan: TestPlan?) {}
        override fun dynamicTestRegistered(testIdentifier: TestIdentifier?) {}
        override fun reportingEntryPublished(testIdentifier: TestIdentifier?, entry: ReportEntry?) {}
        override fun testPlanExecutionFinished(testPlan: TestPlan?) {}
    }

    LauncherFactory.create().execute(request, listener)

    log(1, "TEST RESULTS: $passed PASSED, $failed FAILED, $skipped SKIPPED, $aborted ABORTED")
}