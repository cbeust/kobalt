package com.beust.kobalt.internal

import com.beust.kobalt.api.*
import com.beust.kobalt.misc.log
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

//
// Operations related to the parsing of kobalt-plugin.xml: XML parsing, PluginInfo, etc...
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
// The following classes are used by JAXB to parse the kobalt-plugin.xml file.

/**
 * The root element of kobalt-plugin.xml
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
    var classpathContributors: ClassNameXml? = null

    @XmlElement(name = "project-contributors") @JvmField
    var projectContributors: ClassNameXml? = null

    @XmlElement(name = "init-contributors") @JvmField
    var initContributors: ClassNameXml? = null

    @XmlElement(name = "repo-contributors") @JvmField
    var repoContributors: ClassNameXml? = null

    @XmlElement(name = "compiler-flag-contributors") @JvmField
    var compilerFlagContributors: ClassNameXml? = null

    @XmlElement(name = "compiler-interceptors") @JvmField
    var compilerInterceptors: ClassNameXml? = null

    @XmlElement(name = "source-directories-interceptors") @JvmField
    var sourceDirectoriesInterceptors: ClassNameXml? = null

    @XmlElement(name = "build-directory-interceptors") @JvmField
    var buildDirectoryInterceptors: ClassNameXml? = null

    @XmlElement(name = "run-contributors") @JvmField
    var runContributors: ClassNameXml? = null
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
 * Turn a KobaltPluginXml (the raw content of kobalt-plugin.xml mapped to POJO's) into a PluginInfo object, which
 * contains all the contributors instantiated and other information that Kobalt can actually use. Kobalt code that
 * needs to access plug-in info can then just inject a PluginInfo object.
 */
class PluginInfo(val xml: KobaltPluginXml, val classLoader: ClassLoader?) {
    val plugins = arrayListOf<IPlugin>()
    val projectContributors = arrayListOf<IProjectContributor>()
    val classpathContributors = arrayListOf<IClasspathContributor>()
    val initContributors = arrayListOf<IInitContributor>()
    val repoContributors = arrayListOf<IRepoContributor>()
    val compilerFlagContributors = arrayListOf<ICompilerFlagContributor>()
    val compilerInterceptors = arrayListOf<ICompilerInterceptor>()
    val sourceDirectoriesInterceptors = arrayListOf<ISourceDirectoriesIncerceptor>()
    val buildDirectoryInterceptors = arrayListOf<IBuildDirectoryIncerceptor>()
    val runContributors = arrayListOf<IRunnerContributor>()

    // Future contributors:
    // source files
    // compilers

    companion object {
        val PLUGIN_XML = "META-INF/kobalt-plugin.xml" // Plugins.PLUGIN_XML)

        /**
         * Read Kobalt's own kobalt-plugin.xml.
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
         * Read a general kobalt-plugin.xml.
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
        xml.classpathContributors?.className?.forEach {
            classpathContributors.add(factory.instanceOf(forName(it)) as IClasspathContributor)
        }
        xml.projectContributors?.className?.forEach {
            projectContributors.add(factory.instanceOf(forName(it)) as IProjectContributor)
        }
        xml.initContributors?.className?.forEach {
            initContributors.add(factory.instanceOf(forName(it)) as IInitContributor)
        }
        xml.repoContributors?.className?.forEach {
            repoContributors.add(factory.instanceOf(forName(it)) as IRepoContributor)
        }
        xml.compilerFlagContributors?.className?.forEach {
            compilerFlagContributors.add(factory.instanceOf(forName(it)) as ICompilerFlagContributor)
        }
        xml.compilerInterceptors?.className?.forEach {
            compilerInterceptors.add(factory.instanceOf(forName(it)) as ICompilerInterceptor)
        }
        xml.sourceDirectoriesInterceptors?.className?.forEach {
            sourceDirectoriesInterceptors.add(factory.instanceOf(forName(it)) as ISourceDirectoriesIncerceptor)
        }
        xml.buildDirectoryInterceptors?.className?.forEach {
            buildDirectoryInterceptors.add(factory.instanceOf(forName(it)) as IBuildDirectoryIncerceptor)
        }
        xml.runContributors?.className?.forEach {
            runContributors.add(factory.instanceOf(forName(it)) as IRunnerContributor)
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

