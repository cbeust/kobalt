package com.beust.kobalt.app.java

import com.beust.kobalt.app.BuildGenerator

public class JavaBuildGenerator: BuildGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/java")
    override val defaultTestDirectories = hashSetOf("src/test/java")
    override val directive = "javaProject"
    override val archetypeName = "java"
    override val fileMatch = { f: String -> f.endsWith(".java") }
}
