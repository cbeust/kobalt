package com.beust.kobalt.app.java

import com.beust.kobalt.app.LanguageTemplateGenerator

/**
 * Template for the "java" generator.
 */
class JavaTemplateGenerator : LanguageTemplateGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/java")
    override val defaultTestDirectories = hashSetOf("src/test/java")
    override val directive = "project"
    override val templateName = "java"
    override val templateDescription = "Generate a simple Java project"
    override val fileMatch = { f: String -> f.endsWith(".java") }
    override val instructions = "Now you can run either `./kobaltw test` or `./kobaltw run`"
    override val mainClass = "Main"
    override val fileMap = listOf(
            FileInfo("src/main/java/" + PACKAGE_NAME.replace(".", "/"), "Main.java", "java.mustache"),
            FileInfo("src/test/java/" + PACKAGE_NAME.replace(".", "/"), "ExampleTest.java", "java-test.mustache")
    )
}
