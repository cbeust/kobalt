package com.beust.kobalt.plugin.java

import com.beust.kobalt.app.BuildGenerator
import com.google.inject.Inject

public class JavaBuildGenerator @Inject constructor (val projectInfo: JavaProjectInfo) : BuildGenerator() {
    override val defaultSourceDirectories = projectInfo.defaultSourceDirectories
    override val defaultTestDirectories = projectInfo.defaultTestDirectories
    override val directive = "javaProject"
    override val name = "java"
    override val fileMatch = { f: String -> f.endsWith(".java") }
}
