package com.beust.kobalt.internal

import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.KFiles
import java.io.File

public class TestNgRunner(override val project: Project, override val classpath: List<IClasspathDependency>)
        : GenericTestRunner(project, classpath) {
    override val mainClass = "org.testng.TestNG"

    override val args: List<String>
        get() = arrayListOf<String>().apply {
            if (project.testArgs.size > 0) {
                addAll(project.testArgs)
            } else {
                val testngXml = File(project.directory, KFiles.joinDir("src", "test", "resources", "testng.xml"))
                if (testngXml.exists()) {
                    add(testngXml.absolutePath)
                } else {
                    add("-testclass")
                    addAll(findTestClasses())
                }
            }
        }
}
