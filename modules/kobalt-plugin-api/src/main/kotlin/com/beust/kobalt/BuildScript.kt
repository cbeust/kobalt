package com.beust.kobalt

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.dependency.FileDependency
import java.io.File
import kotlin.collections.forEach
import kotlin.collections.joinToString
import kotlin.collections.toArrayList
import kotlin.text.isNullOrBlank

@Directive
fun homeDir(vararg dirs: String) : String = SystemProperties.homeDir +
        File.separator + dirs.toArrayList().joinToString(File.separator)

@Directive
fun localMavenRepo() = homeDir(".m2" + File.separator + "repository/")

@Directive
fun file(file: String) : String = FileDependency.PREFIX_FILE + file

@Directive
fun plugins(vararg dependency : IClasspathDependency) {
    dependency.forEach { Plugins.addDynamicPlugin(it) }
}

@Directive
fun plugins(vararg dependencies : String) {
    val factory = Kobalt.INJECTOR.getInstance(DepFactory::class.java)
    dependencies.forEach {
        Plugins.addDynamicPlugin(factory.create(it))
    }
}

data class HostConfig(var url: String = "", var username: String? = null, var password: String? = null) {
    fun hasAuth() : Boolean {
        return (! username.isNullOrBlank()) && (! password.isNullOrBlank())
    }

    override fun toString() : String {
        return url + if (username != null) {
            "username: $username, password: ***"
        } else {
            ""
        }
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
fun glob(g: String) : IFileSpec.GlobSpec = IFileSpec.GlobSpec(g)
