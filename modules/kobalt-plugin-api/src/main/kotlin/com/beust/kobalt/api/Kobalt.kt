package com.beust.kobalt.api

import com.beust.kobalt.Constants
import com.beust.kobalt.HostConfig
import com.beust.kobalt.Plugins
import com.google.inject.Injector
import java.io.InputStream
import java.util.*

public class Kobalt {
    companion object {
        // This injector will eventually be replaced with a different injector initialized with the
        // correct arguments (or with a TestModule) but it's necessary to give it a default value
        // here so the kobalt-plugin.xml file can be read since this is done very early
        lateinit var INJECTOR: Injector

        var context: KobaltContext? = null

        /**
         * @return the repos from the build files and from the contributors.
         */
        val repos: Set<HostConfig>
            get() {
                val result = HashSet(reposFromBuildFiles)
                Kobalt.context?.pluginInfo?.repoContributors?.forEach {
                    result.addAll(it.reposFor(null))
                }
                return result
            }

        val reposFromBuildFiles = HashSet<HostConfig>(Constants.DEFAULT_REPOS.map { HostConfig(it) })

        fun addRepo(repo: HostConfig) = reposFromBuildFiles.add(
                if (repo.url.endsWith("/")) repo
                else repo.copy(url = (repo.url + "/")))

        private val PROPERTY_KOBALT_VERSION = "kobalt.version"
        private val KOBALT_PROPERTIES = "kobalt.properties"

        /** kobalt.properties */
        private val kobaltProperties: Properties by lazy { readProperties() }

        /**
         * Read the content of kobalt.properties
         */
        private fun readProperties(): Properties {
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

        val version = kobaltProperties.getProperty(PROPERTY_KOBALT_VERSION)

        fun findPlugin(name: String) = Plugins.findPlugin(name)
    }
}
