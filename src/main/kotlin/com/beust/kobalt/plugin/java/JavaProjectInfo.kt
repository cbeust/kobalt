package com.beust.kobalt.plugin.java

import com.beust.kobalt.internal.IProjectInfo
import com.google.inject.Singleton

@Singleton
class JavaProjectInfo : IProjectInfo {
    override val defaultSourceDirectories = arrayListOf("src/main/java", "src/main/resources")
    override val defaultTestDirectories = arrayListOf("src/test/java", "src/test/resources")
}
