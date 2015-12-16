package com.beust.kobalt.internal

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import java.io.File

public class TestNgRunner() : GenericTestRunner() {

    override val mainClass = "org.testng.TestNG"

    override val dependencyName = "org.testng"

    override fun args(project: Project, classpath: List<IClasspathDependency>) = arrayListOf<String>().apply {
            if (project.testArgs.size > 0) {
                addAll(project.testArgs)
            } else {
                val testngXml = File(project.directory, KFiles.joinDir("src", "test", "resources", "testng.xml"))
                if (testngXml.exists()) {
                    add(testngXml.absolutePath)
                } else {
                    add("-testclass")
                    add(findTestClasses(project, classpath).joinToString(","))
                }
            }
        }
}
