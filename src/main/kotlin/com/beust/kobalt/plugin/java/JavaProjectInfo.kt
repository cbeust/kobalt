package com.beust.kobalt.plugin.java

import com.beust.kobalt.internal.IProjectInfo
import com.google.inject.Singleton

@Singleton
class JavaProjectInfo : IProjectInfo {
    override val sourceDirectory = "java"
    override val defaultSourceDirectories = hashSetOf("src/main/java", "src/main/resources")
    override val defaultTestDirectories = hashSetOf("src/test/java", "src/test/resources")
}
