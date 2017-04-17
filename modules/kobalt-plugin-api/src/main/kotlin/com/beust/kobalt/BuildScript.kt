package com.beust.kobalt

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.KobaltLogger
import org.eclipse.aether.repository.Proxy
import java.io.File
import java.net.InetSocketAddress

var BUILD_SCRIPT_CONFIG : BuildScriptConfig? = null

class BuildScriptConfig {
    /** The list of repos used to locate plug-ins. */
    @Directive
    fun repos(vararg r: String) = newRepos(*r)

    /** The list of plug-ins to use for this build file. */
    @Directive
    fun plugins(vararg pl: String) = newPlugins(*pl)

    /** The build file classpath. */
    @Directive
    fun buildFileClasspath(vararg bfc: String) = newBuildFileClasspath(*bfc)

    /** Options passed to Kobalt */
    @Directive
    fun kobaltOptions(vararg options: String) = Kobalt.addKobaltOptions(options)

    /** Where to find additional build files */
    @Directive
    fun buildSourceDirs(vararg dirs: String) = Kobalt.addBuildSourceDirs(dirs)

    // The following settings modify the compiler used to compile the build file, which regular users should
    // probably never need to do. Projects should use kotlinCompiler { compilerVersion } to configure the
    // Kotin compiler for their source files.
    var kobaltCompilerVersion : String? = null
    var kobaltCompilerRepo: String? = null
    var kobaltCompilerFlags: String? = null
}

@Directive
fun homeDir(vararg dirs: String) : String = SystemProperties.homeDir +
        File.separator + dirs.toMutableList().joinToString(File.separator)

@Directive
fun file(file: String) : String = FileDependency.PREFIX_FILE + file

fun plugins(vararg dependency : IClasspathDependency) {
    dependency.forEach { Plugins.addDynamicPlugin(it) }
}

fun plugins(vararg dependencies : String) {
    KobaltLogger.logger.warn("Build.kt",
            "Invoking plugins() directly is deprecated, use the buildScript{} directive")
    newPlugins(*dependencies)
}

@Directive
fun newPlugins(vararg dependencies : String) {
    val factory = Kobalt.INJECTOR.getInstance(DependencyManager::class.java)
    dependencies.forEach {
        Plugins.addDynamicPlugin(factory.create(it))
    }
}

data class ProxyConfig(val host: String = "", val port: Int = 0, val type: String = "", val nonProxyHosts: String = "") {
    fun toProxy() = java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress(host, port))

    fun toAetherProxy() = Proxy(type, host, port) // TODO make support for proxy auth
}

data class HostConfig(var url: String = "", var name: String = HostConfig.createRepoName(url),
        var username: String? = null, var password: String? = null) {

    companion object {
        /**
         * For repos specified in the build file (repos()) that don't have an associated unique name,
         * create such a name from the URL. This is a requirement from Maven Resolver, and failing to do
         * this leads to very weird resolution errors.
         */
        private fun createRepoName(url: String) = url.replace("/", "_").replace("\\", "_").replace(":", "_")
    }

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

fun repos(vararg repos : String) {
    KobaltLogger.logger.warn("Build.kt",
            "Invoking repos() directly is deprecated, use the buildScript{} directive")
    newRepos(*repos)
}

fun newRepos(vararg repos: String) {
    repos.forEach { Kobalt.addRepo(HostConfig(it)) }
}

fun buildFileClasspath(vararg deps: String) {
    KobaltLogger.logger.warn("Build.kt",
            "Invoking buildFileClasspath() directly is deprecated, use the buildScript{} directive")
    newBuildFileClasspath(*deps)
}

fun newBuildFileClasspath(vararg deps: String) {
    deps.forEach { Kobalt.addBuildFileClasspath(it) }
}

@Directive
fun authRepos(vararg repos : HostConfig) {
    repos.forEach { Kobalt.addRepo(it) }
}

@Directive
fun authRepo(init: HostConfig.() -> Unit) = HostConfig(name = "").apply { init() }

@Directive
fun glob(g: String) : IFileSpec.GlobSpec = IFileSpec.GlobSpec(g)

/**
 * The location of the local Maven repository.
 */
@Directive
fun localMaven() : String {
    val pluginInfo = Kobalt.INJECTOR.getInstance(PluginInfo::class.java)
    val initial = Kobalt.INJECTOR.getInstance(KobaltSettings::class.java).localMavenRepo
    val result = pluginInfo.localMavenRepoPathInterceptors.fold(initial) { current, interceptor ->
        File(interceptor.repoPath(current.absolutePath))
    }
    return result.toURI().toString()
}
