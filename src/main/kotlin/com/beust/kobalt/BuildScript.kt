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

data class HostConfig(var url: String = "", var username: String? = null, var password: String? = null) {
    fun hasAuth() : Boolean {
        return (! username.isNullOrBlank()) && (! password.isNullOrBlank())
    }
}

@Directive
fun repos(vararg repos : String) {
    repos.forEach { Kobalt.addRepo(HostConfig(it)) }
}

@Directive
fun authRepos(vararg repos : HostConfig) {
    repos.forEach { Kobalt.addRepo(it) }
}

@Directive
fun authRepo(init: HostConfig.() -> Unit) = HostConfig().apply { init() }

@Directive
fun glob(g: String) : IFileSpec.Glob = IFileSpec.Glob(g)
