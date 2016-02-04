package com.beust.kobalt.app.java

import com.beust.kobalt.app.BuildGenerator
import com.beust.kobalt.plugin.java.JavaProjectInfo
import com.google.inject.Inject

public class JavaBuildGenerator @Inject constructor (val projectInfo: JavaProjectInfo) : BuildGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/java")
    override val defaultTestDirectories = hashSetOf("src/test/java")
    override val directive = "javaProject"
    override val name = "java"
    override val fileMatch = { f: String -> f.endsWith(".java") }
}
