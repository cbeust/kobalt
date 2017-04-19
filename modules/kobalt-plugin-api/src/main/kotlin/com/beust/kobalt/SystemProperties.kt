package com.beust.kobalt

class SystemProperties {
    companion object {
        val javaBase =
                System.getenv("JAVA_HOME")
                ?: System.getProperty("java.home")
                ?: throw IllegalArgumentException("JAVA_HOME not defined")
        val javaVersion = System.getProperty("java.version")
        val homeDir = System.getProperty("user.home")
        val tmpDir = System.getProperty("java.io.tmpdir")
        val currentDir = System.getProperty("user.dir")
        val username = System.getProperty("user.name")
    }
}

