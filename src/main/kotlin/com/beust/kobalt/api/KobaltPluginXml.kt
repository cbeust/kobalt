package com.beust.kobalt.api

import com.beust.kobalt.maven.IClasspathDependency
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

//
// Operations related to the parsing of plugin.xml: contributors, XML mapping, etc...
//

/////
// Contributors
//

/**
 * Plugins that create project need to implement this interface.
 */
interface IProjectContributor {
    fun projects() : List<ProjectDescription>
}

class ProjectDescription(val project: Project, val dependsOn: List<Project>)

/**
 * Plugins that export classpath entries need to implement this interface.
 */
interface IClasspathContributor {
    fun entriesFor(project: Project) : Collection<IClasspathDependency>
}

/**
 * The factory function to use to instantiate all the contributors and other entities
 * found in plugin.xml.
 */
interface IFactory {
    fun <T> instanceOf(c: Class<T>) : T
}

class ContributorFactory : IFactory {
    override fun <T> instanceOf(c: Class<T>) : T = Kobalt.INJECTOR.getInstance(c)
}

/**
 * Plugins that want to participate in the --init process (they can generate files to initialize
 * a new project).
 */
interface IInitContributor {
    /**
     * How many files your plug-in understands in the given directory. The contributor with the
     * highest number will be asked to generate the build file.
     */
    fun filesManaged(dir: File): Int

    /**
     * Generate the Build.kt file into the given OutputStream.
     */
    fun generateBuildFile(os: OutputStream)
}

/////
// XML parsing
//
// The following classes are used by JAXB to parse the plugin.xml file.

/**
 * The root element of plugin.xml
 */
@XmlRootElement(name = "kobalt-plugin")
class KobaltPluginXml {
    @XmlElement @JvmField
    var name: String? = null

    @XmlElement(name = "factory-class-name") @JvmField
    var factoryClassName: String? = null

    @XmlElement(name = "classpath-contributors") @JvmField
    var classpathContributors : ContributorsXml? = null

    @XmlElement(name = "project-contributors") @JvmField
    var projectContributors : ContributorsXml? = null

    @XmlElement(name = "init-contributors") @JvmField
    var initContributors : ContributorsXml? = null
}

class ContributorXml {
    @XmlElement @JvmField
    val name: String? = null
}

class ContributorsXml {
    @XmlElement(name = "class-name") @JvmField
    var className: List<String> = arrayListOf()
}

/**
 * Turn a KobaltPluginXml (the raw content of plugin.xml mapped to POJO's) into a PluginInfo object, which contains
 * all the contributors instantiated and other information that Kobalt can actually use. Kobalt code that
 * needs to access plug-in info can then just inject a PluginInfo object.
 */
class PluginInfo(val xml: KobaltPluginXml) {
    val projectContributors = arrayListOf<IProjectContributor>()
    val classpathContributors = arrayListOf<IClasspathContributor>()
    val initContributors = arrayListOf<IInitContributor>()

    // Future contributors:
    // compilerArgs
    // source files
    // compilers
    // repos

    companion object {
        /**
         * Read Kobalt's own plugin.xml.
         */
        fun readKobaltPluginXml() : PluginInfo {
            // Note: use forward slash here since we're looking up this file in a .jar file
            val pluginXml = "META-INF/plugin.xml" // Plugins.PLUGIN_XML)
            val url = Kobalt::class.java.classLoader.getResource(pluginXml)
            if (url != null) {
                return readPluginXml(url.openConnection().inputStream)
            } else {
                throw AssertionError("Couldn't find $pluginXml")
            }
        }

        /**
         * Read a general plugin.xml.
         */
        private fun readPluginXml(ins: InputStream): PluginInfo {
            val jaxbContext = JAXBContext.newInstance(KobaltPluginXml::class.java)
            val kotlinPlugin: KobaltPluginXml = jaxbContext.createUnmarshaller().unmarshal(ins)
                    as KobaltPluginXml
            return PluginInfo(kotlinPlugin)
        }
    }

    init {
        val factory = Class.forName(xml.factoryClassName).newInstance() as IFactory
        xml.classpathContributors?.className?.forEach {
            classpathContributors.add(factory.instanceOf(Class.forName(it)) as IClasspathContributor)
        }
        xml.projectContributors?.className?.forEach {
            projectContributors.add(factory.instanceOf(Class.forName(it)) as IProjectContributor)
        }
        xml.initContributors?.className?.forEach {
            initContributors.add(factory.instanceOf(Class.forName(it)) as IInitContributor)
        }
    }
}

