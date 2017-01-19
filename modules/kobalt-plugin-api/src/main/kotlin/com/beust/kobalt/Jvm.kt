package com.beust.kobalt

import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.warn
import java.io.File
import java.io.IOException

open class Jvm constructor(
        val os: OperatingSystem,
        var javaBase: File? = null) : JavaInfo() {

    private var _javaHome: File? = null
    override var javaHome: File? = null
        get() = _javaHome!!
    override var runtimeJar: File? = null
    private fun findRuntimeJar() : File? {
        var runtimeJar = File(javaBase, "lib/rt.jar")
        if (runtimeJar.exists()) {
            return runtimeJar
        }
        runtimeJar = File(javaBase, "jre/lib/rt.jar")
        return if (runtimeJar.exists()) runtimeJar else null
    }
    override var toolsJar: File? = null

    private var userSupplied: Boolean? = false
    private var javaVersion: String? = null

    init {
        if (javaBase == null) {
            //discover based on what's in the sys. property
            try {
                javaBase = File(System.getProperty("java.home")).canonicalFile
            } catch (e: IOException) {
                throw KobaltException(e)
            }

            _javaHome = findJavaHome(javaBase!!)
            javaVersion = SystemProperties.Companion.javaVersion
            userSupplied = false
        } else {
            //precisely use what the user wants and validate strictly further on
            _javaHome = javaBase!!
            userSupplied = true
            javaVersion = null
        }
        toolsJar = findToolsJar(javaBase!!)
        runtimeJar = findRuntimeJar()
    }

    private fun findJavaHome(javaBase: File): File {
        val toolsJar = findToolsJar(javaBase)
        if (toolsJar != null) {
            return toolsJar.parentFile.parentFile
        } else if (javaBase.name.equals("jre", true) && File(javaBase.parentFile,
                "bin/java").exists()) {
            return javaBase.parentFile
        } else {
            return javaBase
        }
    }

    private fun findToolsJar(jh: File): File? {
        javaHome = jh
        var toolsJar = File(javaHome, "lib/tools.jar")
        if (toolsJar.exists()) {
            return toolsJar
        }
        if (javaHome!!.name.equals("jre", true)) {
            javaHome = javaHome!!.parentFile
            toolsJar = File(javaHome, "lib/tools.jar")
            if (toolsJar.exists()) {
                return toolsJar
            }
        }

        if (os.isWindows()) {
            val version = SystemProperties.Companion.javaVersion
            if (javaHome!!.name.toRegex().matches("jre\\d+")
                    || javaHome!!.name == "jre$version") {
                javaHome = File(javaHome!!.parentFile, "jdk$version")
                toolsJar = File(javaHome, "lib/tools.jar")
                if (toolsJar.exists()) {
                    return toolsJar
                }
            }
        }

        return null
    }

//    open fun isIbmJvm(): Boolean {
//        return false
//    }

    override fun findExecutable(command: String): File {
        if (javaHome != null) {
            val jdkHome = if (javaHome!!.endsWith("jre")) javaHome!!.parentFile else javaHome
            val exec = File(jdkHome, "bin/" + command)
            val executable = File(os.getExecutableName(exec.absolutePath))
            if (executable.isFile) {
                return executable
            }
        }

//        if (userSupplied) {
//            //then we want to validate strictly
//            throw JavaHomeException(String.format("The supplied javaHome seems to be invalid." + " I cannot find the %s executable. Tried location: %s", command, executable.getAbsolutePath()))
//        }

        val pathExecutable = os.findInPath(command)
        if (pathExecutable != null) {
            kobaltLog(2, "Unable to find the $command executable using home: " +
                    "$javaHome but found it on the PATH: $pathExecutable.")
            return pathExecutable
        }

        warn("Unable to find the $command executable. Tried the java home: $javaHome" +
                " and the PATH. We will assume the executable can be ran in the current " +
                "working folder.")
        return java.io.File(os.getExecutableName(command))
    }

}

class AppleJvm : Jvm {
    override var runtimeJar: File? = File(javaHome!!.getParentFile(), "Classes/classes.jar")
    override var toolsJar: File? = File(javaHome!!.getParentFile(), "Classes/tools.jar")

    constructor(os: OperatingSystem) : super(os) {
    }

    constructor(current: OperatingSystem, javaHome: File) : super(current, javaHome) {
    }

    /**
     * {@inheritDoc}
     */
//    fun getInheritableEnvironmentVariables(envVars: Map<String, *>): Map<String, *> {
//        val vars = HashMap<String, Any>()
//        for (entry in envVars.entrySet()) {
//            if (entry.getKey().toRegex().matches("APP_NAME_\\d+") ||
//                    entry.getKey().toRegex().matches("JAVA_MAIN_CLASS_\\d+")) {
//                continue
//            }
//            vars.put(entry.getKey(), entry.getValue())
//        }
//        return vars
//    }
}

class IbmJvm(os: OperatingSystem, suppliedJavaBase: File) : Jvm(os, suppliedJavaBase) {
    override var runtimeJar: File? = throw IllegalArgumentException("Not implemented")
    override var toolsJar: File? = throw IllegalArgumentException("Not implemented")

//    override fun isIbmJvm(): Boolean {
//        return true
//    }
}

