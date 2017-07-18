package com.beust.kobalt.app.kotlin

import com.beust.kobalt.app.LanguageTemplateGenerator
import com.beust.kobalt.maven.Pom

class KotlinTemplateGenerator : LanguageTemplateGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/kotlin")
    override val defaultTestDirectories = hashSetOf("src/test/kotlin")
    override val mainDependencies = arrayListOf(
            Pom.Dependency("org.jetbrains.kotlin","kotlin-runtime", null, "1.1.3"),
            Pom.Dependency("org.jetbrains.kotlin", "kotlin-stdlib", null, "1.1.3"))
    override val testDependencies = arrayListOf<Pom.Dependency>()
    override val directive = "project"
    override val templateName = "kotlin"
    override val templateDescription = "Generate a simple Kotlin project"
    override val fileMatch = { f: String -> f.endsWith(".kt") }
    override val mainClass = "MainKt"
    override val instructions = "Now you can run either `./kobaltw test` or `./kobaltw run`"
    override val fileMap = listOf(
            FileInfo("src/main/kotlin/" + PACKAGE_NAME.replace(".", "/"), "Main.kt", "kotlin.mustache"),
            FileInfo("src/test/kotlin/" + PACKAGE_NAME.replace(".", "/"), "MainTest.kt", "kotlin-test.mustache")
    )
}

