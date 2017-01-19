package com.beust.kobalt

import java.io.File

abstract class JavaInfo {
    val javaExecutable: File?
        get() = findExecutable("java")
    val javacExecutable: File?
        get() = findExecutable("javac")
    val javadocExecutable: File?
        get() = findExecutable("javadoc")
    abstract var javaHome: File?
    abstract var runtimeJar: File?
    abstract var toolsJar: File?

    abstract fun findExecutable(command: String) : File

    companion object {
        fun create(javaBase: File?): Jvm {
            val vendor = System.getProperty("java.vm.vendor")
            if (vendor.toLowerCase().startsWith("apple inc.")) {
                return AppleJvm(OperatingSystem.Companion.current(), javaBase!!)
            }
            if (vendor.toLowerCase().startsWith("ibm corporation")) {
                return IbmJvm(OperatingSystem.Companion.current(), javaBase!!)
            }
            return Jvm(OperatingSystem.Companion.current(), javaBase)
        }
    }
}
