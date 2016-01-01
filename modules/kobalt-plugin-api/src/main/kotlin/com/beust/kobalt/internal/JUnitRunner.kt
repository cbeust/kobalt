package com.beust.kobalt.internal

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project

open public class JUnitRunner() : GenericTestRunner() {

    override val mainClass = "org.junit.runner.JUnitCore"

    override val dependencyName = "junit"

    override fun args(project: Project, classpath: List<IClasspathDependency>) = findTestClasses(project)
}

