package com.beust.kobalt.app.kotlin

import com.beust.kobalt.app.BuildGenerator

class KotlinBuildGenerator : BuildGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/kotlin")
    override val defaultTestDirectories = hashSetOf("src/test/kotlin")
    override val directive = "project"
    override val archetypeName = "kotlin"
    override val fileMatch = { f: String -> f.endsWith(".kt") }
}

