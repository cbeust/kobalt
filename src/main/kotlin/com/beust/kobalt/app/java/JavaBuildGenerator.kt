package com.beust.kobalt.app.java

import com.beust.kobalt.app.BuildGenerator

class JavaBuildGenerator: BuildGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/java")
    override val defaultTestDirectories = hashSetOf("src/test/java")
    override val directive = "project"
    override val archetypeName = "java"
    override val archetypeDescription = "Generates a simple Java project"
    override val fileMatch = { f: String -> f.endsWith(".java") }
}
