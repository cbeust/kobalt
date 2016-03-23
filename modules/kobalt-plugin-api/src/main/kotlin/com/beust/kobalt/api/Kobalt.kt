package com.beust.kobalt.api

import com.beust.kobalt.Constants
import com.beust.kobalt.HostConfig
import com.beust.kobalt.Plugins
import com.google.inject.Injector
import java.io.InputStream
import java.time.Duration
import java.util.*

class Kobalt {
    companion object {
        // This injector will eventually be replaced with a different injector initialized with the
        // correct arguments (or with a TestModule) but it's necessary to give it a default value
        // here so the kobalt-plugin.xml file can be read since this is done very early
        lateinit var INJECTOR : Injector

        var context: KobaltContext? = null

        /**
         * @return the repos calculated from the following places:
         * - Either repos specified in settings.xml or from Constants.DEFAULT_REPOS
         * - Repos from the build file
         */
        val repos : Set<HostConfig>
            get() {
                val settingsRepos = Kobalt.context?.settings?.defaultRepos ?: emptyList()
                val result = ArrayList(
                        (if (settingsRepos.isEmpty()) Constants.DEFAULT_REPOS
                        else settingsRepos)
                    .map { HostConfig(it) })

                Kobalt.context?.pluginInfo?.repoContributors?.forEach {
                    result.addAll(it.reposFor(null))
                }

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
