package com.beust.kobalt.internal

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.warn
import java.io.File

public class TestNgRunner() : GenericTestRunner() {

    override val mainClass = "org.testng.TestNG"

    override val dependencyName = "org.testng"

    fun defaultOutput(project: Project) = KFiles.joinDir(project.buildDirectory, "test-output")

    override fun args(project: Project, classpath: List<IClasspathDependency>) = arrayListOf<String>().apply {
        var addOutput = true
        project.testArgs.forEach { arg ->
            if (arg == "-d") addOutput = false
        }

        if (project.testArgs.size == 0) {
            // No arguments, so we'll do it ourselves. Either testng.xml or the list of classes
            val testngXml = File(project.directory, KFiles.joinDir("src", "test", "resources", "testng.xml"))
            if (testngXml.exists()) {
                add(testngXml.absolutePath)
            } else {
                val testClasses = findTestClasses(project)
                if (testClasses.size > 0) {
                    if (addOutput) {
                        add("-d")
                        add(defaultOutput(project))
                    }
                    addAll(project.testArgs)

                    add("-testclass")
                    add(testClasses.joinToString(","))
                } else {
                    warn("Couldn't find any test classes for ${project.name}")
                }
            }
        } else {
            if (addOutput) {
                add("-d")
                add(defaultOutput(project))
            }
            addAll(project.testArgs)
        }
    }
}
