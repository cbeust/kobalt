package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.internal.IProjectInfo
import com.google.inject.Singleton

@Singleton
class KotlinProjectInfo : IProjectInfo {
    override val sourceDirectory = "kotlin"
    override val defaultSourceDirectories = hashSetOf("src/main/kotlin", "src/main/resources")
    override val defaultTestDirectories = hashSetOf("src/test/kotlin", "src/test/resources")
}

