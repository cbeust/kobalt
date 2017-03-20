package com.beust.kobalt.internal

import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.warn
import java.io.File

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
}
