package com.beust.kobalt.internal

import com.beust.kobalt.KobaltException
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
 * The fields in this interface have tests.
 */
interface IPluginInfo {
    val testJvmFlagContributors : List<ITestJvmFlagContributor>
    val testJvmFlagInterceptors : List<ITestJvmFlagInterceptor>
}

open class BasePluginInfo : IPluginInfo {
    override val testJvmFlagContributors = arrayListOf<ITestJvmFlagContributor>()
    override val testJvmFlagInterceptors = arrayListOf<ITestJvmFlagInterceptor>()
}
/**
 * Turn a KobaltPluginXml (the raw content of kobalt-plugin.xml mapped to POJO's) into a PluginInfo object, which
 * contains all the contributors instantiated and other information that Kobalt can actually use. Kobalt code that
 * needs to access plug-in info can then just inject a PluginInfo object.
 */
class PluginInfo(val xml: KobaltPluginXml, val pluginClassLoader: ClassLoader?, val classLoader: ClassLoader?)
        : BasePluginInfo() {
    val plugins = arrayListOf<IPlugin>()
    val projectContributors = arrayListOf<IProjectContributor>()
    val classpathContributors = arrayListOf<IClasspathContributor>()
    val initContributors = arrayListOf<ITemplateContributor>()
    val repoContributors = arrayListOf<IRepoContributor>()
    val compilerFlagContributors = arrayListOf<ICompilerFlagContributor>()
    val compilerInterceptors = arrayListOf<ICompilerInterceptor>()
    val sourceDirectoriesInterceptors = arrayListOf<ISourceDirectoryInterceptor>()
    val buildDirectoryInterceptors = arrayListOf<IBuildDirectoryInterceptor>()
    val runnerContributors = arrayListOf<IRunnerContributor>()
    val testRunnerContributors = arrayListOf<ITestRunnerContributor>()
    val classpathInterceptors = arrayListOf<IClasspathInterceptor>()
    val compilerContributors = arrayListOf<ICompilerContributor>()
    val docContributors = arrayListOf<IDocContributor>()
    val sourceDirContributors = arrayListOf<ISourceDirectoryContributor>()
    val testSourceDirContributors = arrayListOf<ITestSourceDirectoryContributor>()
    val buildConfigFieldContributors = arrayListOf<IBuildConfigFieldContributor>()
    val taskContributors = arrayListOf<ITaskContributor>()
    val assemblyContributors = arrayListOf<IAssemblyContributor>()
    val incrementalAssemblyContributors = arrayListOf<IIncrementalAssemblyContributor>()

    // Not documented yet
    val buildConfigContributors = arrayListOf<IBuildConfigContributor>()
    val mavenIdInterceptors = arrayListOf<IMavenIdInterceptor>()
    val jvmFlagContributors = arrayListOf<IJvmFlagContributor>()
    val incrementalTaskContributors = arrayListOf<IIncrementalTaskContributor>()

    // Note: intentionally repeating them here even though they are defined by our base class so
    // that this class always contains the full list of contributors and interceptors
    override val testJvmFlagContributors = arrayListOf<ITestJvmFlagContributor>()
    override val testJvmFlagInterceptors = arrayListOf<ITestJvmFlagInterceptor>()

    companion object {
        /**
         * Where plug-ins define their plug-in actors.
         */
        val PLUGIN_XML = "META-INF/kobalt-plugin.xml"

        /**
         * Kobalt's core XML file needs to be different from kobalt-plugin.xml because classloaders
         * can put a plug-in's jar file in front of Kobalt's, which means we'll read
         * that one instead of the core one.
         */
        val PLUGIN_CORE_XML = "META-INF/kobalt-core-plugin.xml"

        /**
         * Read Kobalt's own kobalt-plugin.xml.
         */
        fun readKobaltPluginXml(): PluginInfo {
            // Note: use forward slash here since we're looking up this file in a .jar file
            val url = Kobalt::class.java.classLoader.getResource(PLUGIN_CORE_XML)
            log(2, "URL for core kobalt-plugin.xml: $url")
            if (url != null) {
                return readPluginXml(url.openConnection().inputStream)
            } else {
                throw AssertionError("Couldn't find $PLUGIN_XML")
            }
        }

        /**
         * Read a general kobalt-plugin.xml.
         */
        fun readPluginXml(ins: InputStream, pluginClassLoader: ClassLoader? = null,
                classLoader: ClassLoader? = null): PluginInfo {
            val jaxbContext = JAXBContext.newInstance(KobaltPluginXml::class.java)
            val kobaltPlugin: KobaltPluginXml = jaxbContext.createUnmarshaller().unmarshal(ins)
                    as KobaltPluginXml
            log(2, "Parsed plugin XML file, found: " + kobaltPlugin.name)
            val result =
                try {
                    PluginInfo(kobaltPlugin, pluginClassLoader, classLoader)
                } catch(ex: Exception) {
                    throw KobaltException("Couldn't create PluginInfo: " + ex.message, ex)
                }
            return result
        }

        fun readPluginXml(s: String, pluginClassLoader: ClassLoader?, scriptClassLoader: ClassLoader? = null)
                = readPluginXml(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)), pluginClassLoader,
                        scriptClassLoader)
    }

    init {
        val factory = if (xml.factoryClassName != null) {
            Class.forName(xml.factoryClassName).newInstance() as IFactory
        } else {
            GuiceFactory()
        }

        fun forName(className: String) : Class<*> {
            fun loadClass(className: String, classLoader: ClassLoader?) : Class<*>? {
                try {
                    return classLoader?.loadClass(className)
                } catch(ex: ClassNotFoundException) {
                    return null
                }
            }

            val result = loadClass(className, classLoader)
                    ?: Class.forName(className)
                    ?: loadClass(className, pluginClassLoader)
                    ?: throw ClassNotFoundException(className)

            return result
        }

        //
        // Populate pluginInfo with what was found in Kobalt's own kobalt-plugin.xml
        //
        @Suppress("UNCHECKED_CAST")
        xml.pluginActors?.className?.forEach {
            with(factory.instanceOf(forName(it))) {
                // Note: can't use "when" here since the same instance can implement multiple interfaces
                if (this is IBuildConfigFieldContributor) buildConfigFieldContributors.add(this)
                if (this is IBuildDirectoryInterceptor) buildDirectoryInterceptors.add(this)
                if (this is IClasspathContributor) classpathContributors.add(this)
                if (this is IClasspathInterceptor) classpathInterceptors.add(this)
                if (this is ICompilerContributor) compilerContributors.add(this)
                if (this is ICompilerFlagContributor) compilerFlagContributors.add(this)
                if (this is ICompilerInterceptor) compilerInterceptors.add(this)
                if (this is IDocContributor) docContributors.add(this)
                if (this is ITemplateContributor) initContributors.add(this)
                if (this is IPlugin) plugins.add(this)
                if (this is IProjectContributor) projectContributors.add(this)
                if (this is IRepoContributor) repoContributors.add(this)
                if (this is IRunnerContributor) runnerContributors.add(this)
                if (this is ISourceDirectoryContributor) sourceDirContributors.add(this)
                if (this is ISourceDirectoryInterceptor) sourceDirectoriesInterceptors.add(this)
                if (this is ITaskContributor) taskContributors.add(this)
                if (this is ITestRunnerContributor) testRunnerContributors.add(this)
                if (this is IMavenIdInterceptor) mavenIdInterceptors.add(this)
                if (this is ITestSourceDirectoryContributor) testSourceDirContributors.add(this)
                if (this is IBuildConfigContributor) buildConfigContributors.add(this)
                if (this is IAssemblyContributor) assemblyContributors.add(this)
                if (this is IIncrementalAssemblyContributor) incrementalAssemblyContributors.add(this)

                // Not documented yet
                if (this is ITestJvmFlagContributor) testJvmFlagContributors.add(this)
                if (this is ITestJvmFlagInterceptor) testJvmFlagInterceptors.add(this)
                if (this is IJvmFlagContributor) jvmFlagContributors.add(this)
                if (this is IIncrementalTaskContributor) incrementalTaskContributors.add(this)
            }
        }
    }

    fun cleanUp() {
        listOf(projectContributors, classpathContributors, initContributors,
                repoContributors, compilerFlagContributors, compilerInterceptors,
                sourceDirectoriesInterceptors, buildDirectoryInterceptors,
                runnerContributors, testRunnerContributors, classpathInterceptors,
                compilerContributors, docContributors, sourceDirContributors,
                testSourceDirContributors, buildConfigFieldContributors,
                taskContributors, incrementalTaskContributors, assemblyContributors,
                incrementalAssemblyContributors, testJvmFlagInterceptors,
                jvmFlagContributors
            ).forEach {
                it.forEach {
                    it.cleanUpActors()
                }
            }
    }

    /**
     * Add the content of @param[pluginInfo] to this pluginInfo.
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
        incrementalTaskContributors.addAll(pluginInfo.incrementalTaskContributors)
        testSourceDirContributors.addAll(pluginInfo.testSourceDirContributors)
        mavenIdInterceptors.addAll(pluginInfo.mavenIdInterceptors)
        buildConfigContributors.addAll(pluginInfo.buildConfigContributors)
        assemblyContributors.addAll(pluginInfo.assemblyContributors)
        incrementalAssemblyContributors.addAll(pluginInfo.incrementalAssemblyContributors)
        testJvmFlagContributors.addAll(pluginInfo.testJvmFlagContributors)
        testJvmFlagInterceptors.addAll(pluginInfo.testJvmFlagInterceptors)
        jvmFlagContributors.addAll(pluginInfo.jvmFlagContributors)
    }
}

