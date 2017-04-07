package com.beust.kobalt.internal

import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project

open class JUnitRunner() : GenericTestRunner() {

    override val mainClass = "org.junit.runner.JUnitCore"
    override val annotationPackage = "org.junit"
    override val dependencyName = "junit"
    override val runnerName = "JUnit 4"

    override fun args(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            testConfig: TestConfig) = findTestClasses(project, context, testConfig)
}

