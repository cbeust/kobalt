package com.beust.kobalt.api

import com.beust.kobalt.misc.Topological
import com.google.common.collect.ArrayListMultimap
import com.google.inject.Injector
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

public interface ICompilerInfo {
    /** Used to detect what kind of language this project is */
    fun findManagedFiles(dir: File) : List<File>

    /** Used to generate the imports */
    val name: String

    /** Used to generate the imports */
    val directive: String

    val defaultSourceDirectories : ArrayList<String>
    val defaultTestDirectories : ArrayList<String>
}

public class Kobalt {
    companion object {
        lateinit var INJECTOR : Injector

        public val compilers : ArrayList<ICompilerInfo> = arrayListOf()

        public fun registerCompiler(c: ICompilerInfo) {
            compilers.add(c)
        }

        private val DEFAULT_REPOS = arrayListOf(
            "http://repo1.maven.org/maven2/",
            "https://repository.jboss.org/nexus/content/repositories/root_repository/",
            "https://jcenter.bintray.com/"
        )

        val repos = ArrayList<String>(DEFAULT_REPOS)

        fun addRepo(repo: String) = repos.add(if (repo.endsWith("/")) repo else repo + "/")

        private val PROPERTY_KOBALT_VERSION = "kobalt.version"
        private val KOBALT_PROPERTIES = "kobalt.properties"
        private val LOCAL_PROPERTIES = "local.properties"

        private val properties : Properties
            get() = readProperties()

        private fun readProperties() : Properties {
            val result = Properties()

            // kobalt.properties is internal to Kobalt
            val url = Kobalt::class.java.classLoader.getResource(KOBALT_PROPERTIES)
            if (url != null) {
                readProperties(result, url.openConnection().inputStream)
            } else {
                throw IllegalArgumentException("Couldn't find ${KOBALT_PROPERTIES}")
            }

            // local.properties can be used by external users
            Paths.get(LOCAL_PROPERTIES).let { path ->
                if (Files.exists(path)) {
                    Files.newInputStream(path).let {
                        readProperties(result, it)
                    }
                }
            }

            return result
        }

        private fun readProperties(properties: Properties, ins: InputStream) {
            properties.load(ins)
            ins.close()
            properties.forEach { es -> System.setProperty(es.getKey().toString(), es.getValue().toString()) }
        }

        val version = properties.getProperty(PROPERTY_KOBALT_VERSION)

        val topological = Topological<Project>()

        fun declareProjectDependencies(project: Project, projects: Array<out Project>) {
            topological.addEdge(project, projects)
        }

        /**
         * @return the projects sorted topologically.
         */
        fun sortProjects(allProjects: ArrayList<Project>) : List<Project>
             = topological.sort(allProjects)
    }
}
