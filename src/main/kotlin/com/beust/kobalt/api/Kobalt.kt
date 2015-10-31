package com.beust.kobalt.api

import com.beust.kobalt.Plugins
import com.beust.kobalt.misc.Topological
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

        var context: KobaltContext? = null

        fun registerCompiler(c: ICompilerInfo) {
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
            properties.forEach { es -> System.setProperty(es.key.toString(), es.value.toString()) }
        }

        val version = properties.getProperty(PROPERTY_KOBALT_VERSION)

        val topologicalProjects = Topological<Project>()

        /**
         * Used by projects to specify that they depend on other projects, e.g.
         * val p = javaProject(project1, project2) { ... }
         */
        fun declareProjectDependencies(project: Project, projects: Array<out Project>) {
            topologicalProjects.addEdge(project, projects)
        }

        /**
         * @return the projects sorted topologically.
         */
        fun sortProjects(allProjects: ArrayList<Project>) = topologicalProjects.sort(allProjects)

        fun findPlugin(name: String) = Plugins.findPlugin(name)
    }
}
