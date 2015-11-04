package com.beust.kobalt.api

import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.plugin.java.JavaPlugin
import com.beust.kobalt.plugin.kotlin.KotlinPlugin
import java.util.*
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

class ProjectDescription(val project: Project, val dependsOn: List<Project>)

interface IProjectContributor {
    fun projects() : List<ProjectDescription>
}

/**
 * Implement this interface in order to add your own entries to the classpath. A list of contributors
 * can be found on the `KobaltContext`.
 */
interface IClasspathContributor {
    fun entriesFor(project: Project) : Collection<IClasspathDependency>
}

interface IFactory {
    fun <T> instanceOf(c: Class<T>) : T
}

class ContributorFactory : IFactory {
    override fun <T> instanceOf(c: Class<T>) : T = Kobalt.INJECTOR.getInstance(c)
}

/**
 * All the information gathered from the various plugin.xml that were collected.
 */
class PluginInfoDescription {
    fun <T> instanceOf(c: Class<T>) : T = Kobalt.INJECTOR.getInstance(c)

    val projectContributors : ArrayList<Class<out IProjectContributor>> =
            arrayListOf(JavaPlugin::class.java, KotlinPlugin::class.java)

    val classpathContributors: ArrayList<Class<out IClasspathContributor>> =
            arrayListOf(KotlinPlugin::class.java)

    // Future contributors:
    // compilerArgs
    // source files
    // compilers
    // --init
}

/**
 * Turn the classes found in PluginInfoDescription into concrete objects that plugins can then use.
 */
class PluginInfo(val description: PluginInfoDescription?) {
    val projectContributors = arrayListOf<IProjectContributor>()
    val classpathContributors = arrayListOf<IClasspathContributor>()

    companion object {
        fun create(xml: KobaltPluginXml) : PluginInfo {
            val factory = Class.forName(xml.factoryClassName).newInstance() as IFactory
            val result = PluginInfo(null)
            xml.classpathContributors?.className?.forEach {
                result.classpathContributors.add(factory.instanceOf(Class.forName(it)) as IClasspathContributor)
            }
            xml.projectContributors?.className?.forEach {
                result.projectContributors.add(factory.instanceOf(Class.forName(it)) as IProjectContributor)
            }
            return result
        }
    }

    init {
        if (description != null) {
            classpathContributors.addAll(description.classpathContributors.map { description.instanceOf(it) })
            projectContributors.addAll(description.projectContributors.map { description.instanceOf(it) })
        }
    }
}

class ContributorXml {
    @XmlElement @JvmField
    val name: String? = null
}

class ContributorsXml {
    @XmlElement(name = "class-name") @JvmField
    var className: List<String> = arrayListOf<String>()
}

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

