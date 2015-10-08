package com.beust.kobalt.internal

import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import java.io.File

public class JUnitRunner(override val project: Project, override val classpath: List<IClasspathDependency>)
        : GenericTestRunner(project, classpath) {

    override val mainClass = "org.junit.runner.JUnitCore"
    override val args = findTestClasses()
}

