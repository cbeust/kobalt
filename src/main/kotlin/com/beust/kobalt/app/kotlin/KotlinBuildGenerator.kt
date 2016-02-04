package com.beust.kobalt.app.kotlin

import com.beust.kobalt.app.BuildGenerator
import com.beust.kobalt.plugin.kotlin.KotlinProjectInfo
import com.google.inject.Inject

public class KotlinBuildGenerator @Inject constructor (val projectInfo: KotlinProjectInfo) : BuildGenerator() {
    override val defaultSourceDirectories = hashSetOf("src/main/kotlin")
    override val defaultTestDirectories = hashSetOf("src/test/kotlin")
    override val directive = "kotlinProject"
    override val name = "kotlin"
    override val fileMatch = { f: String -> f.endsWith(".kt") }
}

