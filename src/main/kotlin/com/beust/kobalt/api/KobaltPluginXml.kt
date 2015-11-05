package com.beust.kobalt.api

import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.KFiles
import java.io.InputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/////
// Contributors
//

class ProjectDescription(val project: Project, val dependsOn: List<Project>)

/**
 * Implement this interface in order to add your own projects.
 */
interface IProjectContributor {
    fun projects() : List<ProjectDescription>
}

/**
 * Implement this interface to add your own entries to the classpath.
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
}

class ContributorXml {
    @XmlElement @JvmField
    val name: String? = null
}

class ContributorsXml {
    @XmlElement(name = "class-name") @JvmField
    var className: List<String> = arrayListOf<String>()
}

/**
 * Turn a KobaltPluginXml (the raw content of plugin.xml) into a PluginInfo object, which contains
 * all the contributors instantiated and other information that Kobalt can actually use.
 */
class PluginInfo(val xml: KobaltPluginXml) {
    val projectContributors = arrayListOf<IProjectContributor>()
    val classpathContributors = arrayListOf<IClasspathContributor>()
    // Future contributors:
    // compilerArgs
    // source files
    // compilers
    // --init
    // repos

    companion object {
        /**
         * Read Kobalt's own plugin.xml.
         */
        fun readKobaltPluginXml() : PluginInfo {
            val pluginXml = KFiles.joinDir("META-INF", "plugin.xml") // Plugins.PLUGIN_XML)
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
    }
}

