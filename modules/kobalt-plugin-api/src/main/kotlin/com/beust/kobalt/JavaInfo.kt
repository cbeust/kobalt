package com.beust.kobalt

import java.io.File

abstract class JavaInfo {
    var javaExecutable: File? = null
        get() = findExecutable("java")
    var javacExecutable: File? = null
        get() = findExecutable("javac")
    var javadocExecutable: File? = null
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
