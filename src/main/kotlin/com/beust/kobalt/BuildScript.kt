package com.beust.kobalt

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KobaltExecutors
import java.io.File

@Directive
fun homeDir(vararg dirs: String) : String = SystemProperties.homeDir +
        File.separator + dirs.toArrayList().joinToString(File.separator)

@Directive
fun file(file: String) : String = FileDependency.PREFIX_FILE + file

@Directive
fun plugins(vararg dependency : IClasspathDependency) {
    Plugins.dynamicPlugins.addAll(dependency)
}

@Directive
fun plugins(vararg dependencies : String) {
    val executor = Kobalt.INJECTOR.getInstance(KobaltExecutors::class.java).miscExecutor
    val factory = Kobalt.INJECTOR.getInstance(DepFactory::class.java)
    dependencies.forEach {
        Plugins.dynamicPlugins.add(factory.create(it, executor))
    }
}

data class HostInfo(val url: String, var keyUsername: String? = null, var keyPassword: String? = null) {
    fun hasAuth() : Boolean {
        return (! keyUsername.isNullOrBlank()) && (! keyPassword.isNullOrBlank())
    }
}

@Directive
fun repos(vararg repos : String) {
    repos.forEach { Kobalt.addRepo(HostInfo(it)) }
}

@Directive
fun repos(vararg repos : HostInfo) {
    repos.forEach { Kobalt.addRepo(it) }
}

class HostConfig(var keyUsername: String? = null, var keyPassword: String? = null)

@Directive
fun authRepo(url: String, init: HostConfig.() -> Unit) : HostInfo {
    val r = HostConfig()
    r.init()
    return HostInfo(url, r.keyUsername, r.keyPassword)
}

@Directive
fun glob(g: String) : IFileSpec.Glob = IFileSpec.Glob(g)
