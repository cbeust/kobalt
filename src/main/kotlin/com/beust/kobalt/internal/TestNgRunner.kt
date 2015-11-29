package com.beust.kobalt.internal

import com.beust.kobalt.api.IAffinity
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.dependency.IClasspathDependency
import com.beust.kobalt.misc.KFiles
import java.io.File

public class TestNgRunner() : GenericTestRunner() {

    override val mainClass = "org.testng.TestNG"

    override fun affinity(project: Project, context: KobaltContext) =
        if (project.testDependencies.any { it.id.contains("testng")}) IAffinity.DEFAULT_POSITIVE_AFFINITY
        else 0

    override fun args(project: Project, classpath: List<IClasspathDependency>) = arrayListOf<String>().apply {
            if (project.testArgs.size > 0) {
                addAll(project.testArgs)
            } else {
                val testngXml = File(project.directory, KFiles.joinDir("src", "test", "resources", "testng.xml"))
                if (testngXml.exists()) {
                    add(testngXml.absolutePath)
                } else {
                    add("-testclass")
                    addAll(findTestClasses(project, classpath))
                }
            }
        }
}
