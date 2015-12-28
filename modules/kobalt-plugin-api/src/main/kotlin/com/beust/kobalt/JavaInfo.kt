package com.beust.kobalt

import java.io.File

abstract public class JavaInfo {
    public var javaExecutable: File? = null
        get() = findExecutable("java")
    public var javacExecutable: File? = null
        get() = findExecutable("javac")
    public var javadocExecutable: File? = null
        get() = findExecutable("javadoc")
    abstract public var javaHome: File?
    abstract public var runtimeJar: File?
    abstract public var toolsJar: File?

    abstract public fun findExecutable(command: String): File

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
