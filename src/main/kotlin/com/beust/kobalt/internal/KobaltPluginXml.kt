package com.beust.kobalt.internal

import com.beust.kobalt.api.*
import com.beust.kobalt.misc.log
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

//
// Operations related to the parsing of plugin.xml: XML parsing, PluginInfo, etc...
//

/**
 * If a plug-in didn't specify a factory, we use our own injector to instantiate all its components.
 */
class GuiceFactory : IFactory {
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

    @XmlElement(name = "plugins") @JvmField
    var plugins : ClassNameXml? = null

    @XmlElement(name = "factory-class-name") @JvmField
    var factoryClassName: String? = null

    @XmlElement(name = "classpath-contributors") @JvmField
    var classpathClassName: ClassNameXml? = null

    @XmlElement(name = "project-contributors") @JvmField
    var projectClassName: ClassNameXml? = null

    @XmlElement(name = "init-contributors") @JvmField
    var initClassName: ClassNameXml? = null

    @XmlElement(name = "repo-contributors") @JvmField
    var repoClassName: ClassNameXml? = null
}

class ContributorXml {
    @XmlElement @JvmField
    val name: String? = null
}

class ClassNameXml {
    @XmlElement(name = "class-name") @JvmField
    var className: List<String> = arrayListOf()
}

/**
 * Turn a KobaltPluginXml (the raw content of plugin.xml mapped to POJO's) into a PluginInfo object, which contains
 * all the contributors instantiated and other information that Kobalt can actually use. Kobalt code that
 * needs to access plug-in info can then just inject a PluginInfo object.
 */
class PluginInfo(val xml: KobaltPluginXml, val classLoader: ClassLoader?) {
    val plugins = arrayListOf<IPlugin>()
    val projectContributors = arrayListOf<IProjectContributor>()
    val classpathContributors = arrayListOf<IClasspathContributor>()
    val initContributors = arrayListOf<IInitContributor>()
    val repoContributors = arrayListOf<IRepoContributor>()

    // Future contributors:
    // compilerArgs
    // source files
    // compilers
    // repos

    companion object {
        val PLUGIN_XML = "META-INF/plugin.xml" // Plugins.PLUGIN_XML)

        /**
         * Read Kobalt's own plugin.xml.
         */
        fun readKobaltPluginXml(): PluginInfo {
            // Note: use forward slash here since we're looking up this file in a .jar file
            val url = Kobalt::class.java.classLoader.getResource(PLUGIN_XML)
            if (url != null) {
                return readPluginXml(url.openConnection().inputStream)
            } else {
                throw AssertionError("Couldn't find $PLUGIN_XML")
            }
        }

        /**
         * Read a general plugin.xml.
         */
        fun readPluginXml(ins: InputStream, classLoader: ClassLoader? = null): PluginInfo {
            val jaxbContext = JAXBContext.newInstance(KobaltPluginXml::class.java)
            val kotlinPlugin: KobaltPluginXml = jaxbContext.createUnmarshaller().unmarshal(ins)
                    as KobaltPluginXml
            return PluginInfo(kotlinPlugin, classLoader)
        }

        fun readPluginXml(s: String, classLoader: ClassLoader? = null)
                = readPluginXml(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)), classLoader)
    }

    init {
        val factory = if (xml.factoryClassName != null) {
            Class.forName(xml.factoryClassName).newInstance() as IFactory
        } else {
            GuiceFactory()
        }

        fun forName(className: String) =
            if (classLoader != null) classLoader.loadClass(className)
            else Class.forName(className)

        xml.plugins?.className?.forEach {
            plugins.add(factory.instanceOf(forName(it)) as IPlugin)
        }
        xml.classpathClassName?.className?.forEach {
            classpathContributors.add(factory.instanceOf(forName(it)) as IClasspathContributor)
        }
        xml.projectClassName?.className?.forEach {
            projectContributors.add(factory.instanceOf(forName(it)) as IProjectContributor)
        }
        xml.initClassName?.className?.forEach {
            initContributors.add(factory.instanceOf(forName(it)) as IInitContributor)
        }
        xml.repoClassName?.className?.forEach {
            repoContributors.add(factory.instanceOf(forName(it)) as IRepoContributor)
        }
    }

    fun addPluginInfo(pluginInfo: PluginInfo) {
        log(2, "Found new plug-in, adding it to pluginInfo: $pluginInfo")

        plugins.addAll(pluginInfo.plugins)
        classpathContributors.addAll(pluginInfo.classpathContributors)
        projectContributors.addAll(pluginInfo.projectContributors)
        initContributors.addAll(pluginInfo.initContributors)
        repoContributors.addAll(pluginInfo.repoContributors)
    }
}

