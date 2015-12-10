package com.beust.kobalt.internal

import com.beust.kobalt.api.*
import com.beust.kobalt.misc.log
import java.io.ByteArrayInputStream
import java.io.File
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

    @XmlElement(name = "plugin-actors") @JvmField
    var pluginActors : ClassNameXml? = null

    @XmlElement(name = "factory-class-name") @JvmField
    var factoryClassName: String? = null
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
    val initContributors = arrayListOf<IInitContributor<File>>()
    val repoContributors = arrayListOf<IRepoContributor>()
    val compilerFlagContributors = arrayListOf<ICompilerFlagContributor>()
    val compilerInterceptors = arrayListOf<ICompilerInterceptor>()
    val sourceDirectoriesInterceptors = arrayListOf<ISourceDirectoryIncerceptor>()
    val buildDirectoryInterceptors = arrayListOf<IBuildDirectoryIncerceptor>()
    val runnerContributors = arrayListOf<IRunnerContributor>()
    val testRunnerContributors = arrayListOf<ITestRunnerContributor>()
    val classpathInterceptors = arrayListOf<IClasspathInterceptor>()
    val compilerContributors = arrayListOf<ICompilerContributor>()
    val docContributors = arrayListOf<IDocContributor>()
    val sourceDirContributors = arrayListOf<ISourceDirectoryContributor>()
    val testSourceDirContributors = arrayListOf<ITestSourceDirectoryContributor>()
    val buildConfigFieldContributors = arrayListOf<IBuildConfigFieldContributor>()
    val taskContributors = arrayListOf<ITaskContributor>()

    val mavenIdInterceptors = arrayListOf<IMavenIdInterceptor>()

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

        //
        // Populate pluginInfo with what was found in Kobalt's own kobalt-plugin.xml
        //
        @Suppress("UNCHECKED_CAST")
        xml.pluginActors?.className?.forEach {
            with(factory.instanceOf(forName(it))) {
                // Note: can't use "when" here since the same instance can implement multiple interfaces
                if (this is IBuildConfigFieldContributor) buildConfigFieldContributors.add(this)
                if (this is IBuildDirectoryIncerceptor) buildDirectoryInterceptors.add(this)
                if (this is IClasspathContributor) classpathContributors.add(this)
                if (this is IClasspathInterceptor) classpathInterceptors.add(this)
                if (this is ICompilerContributor) compilerContributors.add(this)
                if (this is ICompilerFlagContributor) compilerFlagContributors.add(this)
                if (this is ICompilerInterceptor) compilerInterceptors.add(this)
                if (this is IDocContributor) docContributors.add(this)
                if (this is IInitContributor<*>) initContributors.add(this as IInitContributor<File>)
                if (this is IPlugin) plugins.add(this)
                if (this is IProjectContributor) projectContributors.add(this)
                if (this is IRepoContributor) repoContributors.add(this)
                if (this is IRunnerContributor) runnerContributors.add(this)
                if (this is ISourceDirectoryContributor) sourceDirContributors.add(this)
                if (this is ISourceDirectoryIncerceptor) sourceDirectoriesInterceptors.add(this)
                if (this is ITaskContributor) taskContributors.add(this)
                if (this is ITestRunnerContributor) testRunnerContributors.add(this)

                // Not documented yet
                if (this is IMavenIdInterceptor) mavenIdInterceptors.add(this)
                if (this is ITestSourceDirectoryContributor) testSourceDirContributors.add(this)
            }
        }
    }

    /**
     * Populate pluginInfo with what was found in the plug-in's kobalt-plugin.xml
     */
    fun addPluginInfo(pluginInfo: PluginInfo) {
        log(2, "Found new plug-in, adding it to pluginInfo: $pluginInfo")

        plugins.addAll(pluginInfo.plugins)
        classpathContributors.addAll(pluginInfo.classpathContributors)
        projectContributors.addAll(pluginInfo.projectContributors)
        initContributors.addAll(pluginInfo.initContributors)
        repoContributors.addAll(pluginInfo.repoContributors)
        compilerFlagContributors.addAll(pluginInfo.compilerFlagContributors)
        compilerInterceptors.addAll(pluginInfo.compilerInterceptors)
        sourceDirectoriesInterceptors.addAll(pluginInfo.sourceDirectoriesInterceptors)
        buildDirectoryInterceptors.addAll(pluginInfo.buildDirectoryInterceptors)
        runnerContributors.addAll(pluginInfo.runnerContributors)
        testRunnerContributors.addAll(pluginInfo.testRunnerContributors)
        classpathInterceptors.addAll(pluginInfo.classpathInterceptors)
        compilerContributors.addAll(pluginInfo.compilerContributors)
        docContributors.addAll(pluginInfo.docContributors)
        sourceDirContributors.addAll(pluginInfo.sourceDirContributors)
        buildConfigFieldContributors.addAll(pluginInfo.buildConfigFieldContributors)
        taskContributors.addAll(pluginInfo.taskContributors)
        testSourceDirContributors.addAll(pluginInfo.testSourceDirContributors)
        mavenIdInterceptors.addAll(pluginInfo.mavenIdInterceptors)
    }
}

