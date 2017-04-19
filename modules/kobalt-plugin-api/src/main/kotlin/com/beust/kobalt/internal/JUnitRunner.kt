package com.beust.kobalt.internal

import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.google.inject.Inject
import java.lang.reflect.Modifier
import java.net.URLClassLoader

open class JUnitRunner() : GenericTestRunner() {

    override val mainClass = "org.junit.runner.JUnitCore"
    override val annotationPackage = "org.junit"
    override val dependencyName = "junit"
    override val runnerName = "JUnit 4"

    override fun args(project: Project, context: KobaltContext, classpath: List<IClasspathDependency>,
            testConfig: TestConfig) = findTestClasses(project, context, testConfig)

    @Inject
    lateinit var dependencyManager: DependencyManager

    override fun filterTestClasses(project: Project, context: KobaltContext, classes: List<String>) : List<String> {
        val deps = dependencyManager.testDependencies(project, context)
        val cl = URLClassLoader(deps.map { it.jarFile.get().toURI().toURL() }.toTypedArray())
        return classes.filter { !Modifier.isAbstract(cl.loadClass(it).modifiers) }
    }

}

