package com.beust.kobalt.internal

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project

open public class JUnitRunner() : GenericTestRunner() {

    override val mainClass = "org.junit.runner.JUnitCore"

    override val dependencyName = "junit"

    override fun args(project: Project, classpath: List<IClasspathDependency>)
            = findTestClasses(project, classpath) {
        // Only return a class if it contains at least one @Test method, otherwise
        // JUnit 4 throws an exception :-(
        it.declaredMethods.flatMap {
            it.annotations.toList()
        }.filter {
            ann: Annotation ->
            ann.javaClass.name.contains("Test")
        }.size > 0
    }
}

