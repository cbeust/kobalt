package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.internal.IProjectInfo
import com.google.inject.Singleton

@Singleton
class KotlinProjectInfo : IProjectInfo {
    override val defaultSourceDirectories = arrayListOf("src/main/kotlin", "src/main/resources")
    override val defaultTestDirectories = arrayListOf("src/test/kotlin", "src/test/resources")
}

