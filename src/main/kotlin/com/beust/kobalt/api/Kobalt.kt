package com.beust.kobalt.api

import com.beust.kobalt.Args
import com.beust.kobalt.Plugins
import com.beust.kobalt.HostInfo
import com.beust.kobalt.misc.MainModule
import com.google.inject.Guice
import com.google.inject.Injector
import java.io.InputStream
import java.util.*

public class Kobalt {
    companion object {
        // This injector will eventually be replaced with a different injector initialized with the
        // correct arguments (or with a TestModule) but it's necessary to give it a default value
        // here so the kobalt-plugin.xml file can be read since this is done very early
        var INJECTOR : Injector = Guice.createInjector(MainModule(Args()))

        var context: KobaltContext? = null

        private val DEFAULT_REPOS = arrayListOf(
            "http://repo1.maven.org/maven2/",
            "https://repository.jboss.org/nexus/content/repositories/root_repository/",
            "https://jcenter.bintray.com/"
        )

        val repos = HashSet<HostInfo>(DEFAULT_REPOS.map { HostInfo(it) })

        fun addRepo(repo: HostInfo) = repos.add(
                if (repo.url.endsWith("/")) repo
                else repo.copy(url = (repo.url + "/")))

        private val PROPERTY_KOBALT_VERSION = "kobalt.version"
        private val KOBALT_PROPERTIES = "kobalt.properties"

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

        val version = kobaltProperties.getProperty(PROPERTY_KOBALT_VERSION)

        fun findPlugin(name: String) = Plugins.findPlugin(name)
    }
}
