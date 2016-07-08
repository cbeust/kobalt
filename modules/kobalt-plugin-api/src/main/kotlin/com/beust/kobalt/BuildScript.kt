package com.beust.kobalt

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.FileDependency
import org.eclipse.aether.repository.Proxy
import java.io.File
import java.net.InetSocketAddress

@Directive
fun homeDir(vararg dirs: String) : String = SystemProperties.homeDir +
        File.separator + dirs.toMutableList().joinToString(File.separator)

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
    val factory = Kobalt.INJECTOR.getInstance(DependencyManager::class.java)
    dependencies.forEach {
        Plugins.addDynamicPlugin(factory.create(it))
    }
}

data class ProxyConfig(val host: String = "", val port: Int = 0, val type: String = "", val nonProxyHosts: String = "") {
    fun toProxy() = java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress(host, port))

    fun toAetherProxy() = Proxy(type, host, port) // TODO make support for proxy auth
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
fun buildFileClasspath(vararg deps: String) {
    deps.forEach { Kobalt.addBuildFileClasspath(it) }
}

@Directive
fun authRepos(vararg repos : HostConfig) {
    repos.forEach { Kobalt.addRepo(it) }
}

@Directive
fun authRepo(init: HostConfig.() -> Unit) = HostConfig().apply { init() }

@Directive
fun glob(g: String) : IFileSpec.GlobSpec = IFileSpec.GlobSpec(g)

@Directive
fun localMaven() = file(Kobalt.INJECTOR.getInstance(KobaltSettings::class.java).localMavenRepo.path)
