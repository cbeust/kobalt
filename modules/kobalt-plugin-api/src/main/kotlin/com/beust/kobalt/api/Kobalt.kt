package com.beust.kobalt.api

import com.beust.kobalt.Constants
import com.beust.kobalt.HostConfig
import com.beust.kobalt.Plugins
import com.beust.kobalt.ProxyConfig
import com.google.inject.Injector
import org.eclipse.aether.repository.Proxy
import com.beust.kobalt.internal.PluginInfo
import com.google.inject.Guice
import com.google.inject.Module
import java.io.InputStream
import java.net.InetSocketAddress
import java.time.Duration
import java.util.*

class Kobalt {
    companion object {
        lateinit var INJECTOR : Injector

        fun init(module: Module) {
            Kobalt.INJECTOR = Guice.createInjector(module)

            //
            // Add all the plugins read in kobalt-plugin.xml to the Plugins singleton, so that code
            // in the build file that calls Plugins.findPlugin() can find them (code in the
            // build file do not have access to the KobaltContext).
            //
            val pluginInfo = Kobalt.INJECTOR.getInstance(PluginInfo::class.java)
            pluginInfo.plugins.forEach { Plugins.addPluginInstance(it) }
        }

        var context: KobaltContext? = null

        val proxyConfig = with(Kobalt.context?.settings?.proxy) {
                if (this != null) {
                    ProxyConfig(host, port.toIntOr(0), type)
                } else null
            }

        fun String.toIntOr(defaultValue: Int) = try {   //TODO can be extracted to some global Utils
            toInt()
        } catch(e: NumberFormatException) {
            defaultValue
        }

        /**
         * @return the repos calculated from the following places:
         * - Either repos specified in settings.xml or from Constants.DEFAULT_REPOS
         * - Repos from the build file
         */
        val repos : Set<HostConfig>
            get() {
                val settingsRepos = Kobalt.context?.settings?.defaultRepos ?: emptyList()
                // Repos from <default-repos> in the settings
                val result = ArrayList(
                        (if (settingsRepos.isEmpty()) Constants.DEFAULT_REPOS
                        else settingsRepos)
                    .map { HostConfig(it) })

                // Repo from <kobalt-compiler-repo> in the settings
                Kobalt.context?.settings?.kobaltCompilerRepo?.let {
                    result.add(HostConfig(it))
                }

                // Repos from the repo contributors
                Kobalt.context?.pluginInfo?.repoContributors?.forEach {
                    result.addAll(it.reposFor(null))
                }

                // Repos from the build file
                result.addAll(reposFromBuildFiles)

                return result.toHashSet()
            }

        val reposFromBuildFiles = hashSetOf<HostConfig>()

        fun addRepo(repo: HostConfig) = reposFromBuildFiles.add(
                if (repo.url.endsWith("/")) repo
                else repo.copy(url = (repo.url + "/")))

        private val KOBALT_PROPERTIES = "kobalt.properties"
        private val PROPERTY_KOBALT_VERSION = "kobalt.version"
        private val PROPERTY_KOBALT_VERSION_CHECK_TIMEOUT = "kobalt.version.checkTimeout"  // ISO-8601

        /** kobalt.properties */
        private val kobaltProperties: Properties by lazy { readProperties() }

        /**
         * Read the content of kobalt.properties
         */
        private fun readProperties() : Properties {
            val result = Properties()

            // kobalt.properties is internal to Kobalt
            val url = Kobalt::class.java.classLoader.getResource(KOBALT_PROPERTIES)
            if (url != null) {
                readProperties(result, url.openConnection().inputStream)
            } else {
                throw AssertionError("Couldn't find $KOBALT_PROPERTIES")
            }

            // local.properties can be used by external users
//            Paths.get(LOCAL_PROPERTIES).let { path ->
//                if (Files.exists(path)) {
//                    Files.newInputStream(path).use {
//                        readProperties(result, it)
//                    }
//                }
//            }

            return result
        }

        private fun readProperties(properties: Properties, ins: InputStream) {
            properties.load(ins)
            ins.close()
            properties.forEach { es -> System.setProperty(es.key.toString(), es.value.toString()) }
        }

        val version: String
            get() = kobaltProperties.getProperty(PROPERTY_KOBALT_VERSION)

        // Note: Duration is Java 8 only, might need an alternative if we want to support Java < 8
        val versionCheckTimeout: Duration
            get() = Duration.parse( kobaltProperties.getProperty(PROPERTY_KOBALT_VERSION_CHECK_TIMEOUT) ?: "P1D")

        fun findPlugin(name: String) = Plugins.findPlugin(name)
    }
}


fun ProxyConfig?.toProxy() = if (this != null) {
    java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress(host, port))
} else null

fun ProxyConfig?.toAetherProxy() = if (this != null) Proxy(type, host, port) else null //TODO make support for proxy auth
