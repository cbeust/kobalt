package com.beust.kobalt.app.kotlin

import com.beust.kobalt.app.BuildGenerator

class KotlinBuildGenerator : BuildGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/kotlin")
    override val defaultTestDirectories = hashSetOf("src/test/kotlin")
    override val directive = "project"
    override val templateName = "kotlin"
    override val templateDescription = "Generate a simple Kotlin project"
    override val fileMatch = { f: String -> f.endsWith(".kt") }
    override val mainClass = "MainKt"
    override val fileMap = listOf(
            FileInfo("src/main/kotlin/" + PACKAGE_NAME.replace(".", "/"), "Main.kt", "kotlin.mustache"),
            FileInfo("src/test/kotlin/" + PACKAGE_NAME.replace(".", "/"), "MainTest.kt", "kotlin-test.mustache")
    )
}

