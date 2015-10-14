package com.beust.kobalt.internal

import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.IClasspathDependency

public class JUnitRunner(override val project: Project, override val classpath: List<IClasspathDependency>)
        : GenericTestRunner(project, classpath) {

    override val mainClass = "org.junit.runner.JUnitCore"
    override val args = findTestClasses()
}

