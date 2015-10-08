package com.beust.kobalt

import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject

public class SystemProperties {
    companion object {
        val javaBase = System.getenv("JAVA_HOME") ?: throw IllegalArgumentException("JAVA_HOME not defined")
        val javaVersion = System.getProperty("java.version")
        val homeDir = System.getProperty("user.home")
        val tmpDir = System.getProperty("java.io.tmpdir")
        val currentDir = System.getProperty("user.dir")
        val username = System.getProperty("user.name")
    }
}

