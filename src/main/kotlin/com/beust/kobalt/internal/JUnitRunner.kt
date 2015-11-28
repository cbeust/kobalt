package com.beust.kobalt.internal

import com.beust.kobalt.api.IRunnerContributor
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.IClasspathDependency

public class JUnitRunner() : GenericTestRunner() {

    override val mainClass = "org.junit.runner.JUnitCore"

    override fun runAffinity(project: Project, context: KobaltContext) =
            if (project.testDependencies.any { it.id.contains("junit")}) IRunnerContributor.DEFAULT_POSITIVE_AFFINITY
            else 0

    override fun args(project: Project, classpath: List<IClasspathDependency>) = findTestClasses(project, classpath)

}

