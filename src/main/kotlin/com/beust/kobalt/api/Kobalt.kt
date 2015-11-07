package com.beust.kobalt.api

import com.beust.kobalt.Plugins
import com.google.inject.Injector
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

public class Kobalt {
    companion object {
        lateinit var INJECTOR : Injector

        var context: KobaltContext? = null

        private val DEFAULT_REPOS = arrayListOf(
            "http://repo1.maven.org/maven2/",
            "https://repository.jboss.org/nexus/content/repositories/root_repository/",
            "https://jcenter.bintray.com/"
        )

        val repos = HashSet<String>(DEFAULT_REPOS)

        fun addRepo(repo: String) = repos.add(if (repo.endsWith("/")) repo else repo + "/")

        private val PROPERTY_KOBALT_VERSION = "kobalt.version"
        private val KOBALT_PROPERTIES = "kobalt.properties"
        private val LOCAL_PROPERTIES = "local.properties"

        private val properties : Properties by lazy { readProperties() }

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
            Paths.get(LOCAL_PROPERTIES).let { path ->
                if (Files.exists(path)) {
                    Files.newInputStream(path).use {
                        readProperties(result, it)
                    }
                }
            }

            return result
        }

        private fun readProperties(properties: Properties, ins: InputStream) {
            properties.load(ins)
            ins.close()
            properties.forEach { es -> System.setProperty(es.key.toString(), es.value.toString()) }
        }

        val version = properties.getProperty(PROPERTY_KOBALT_VERSION)

        fun findPlugin(name: String) = Plugins.findPlugin(name)
    }
}
