package com.beust.kobalt.maven

import org.w3c.dom.Element
import java.io.File
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAnyElement
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "project")
class PomProject {
    var modelVersion: String? = null
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null
    var name: String? = null
    var description: String? = null
    var url: String? = null
    var packaging: String? = null
    var licenses : Licenses? = null
    var developers: Developers? = null
    var scm: Scm? = null
    var properties: Properties? = null
    var parent: Parent? = null
    var dependencies: Dependencies? = null
    var pluginRepositories: PluginRepositories? = null

    val propertyMap = hashMapOf<String, String>()
    fun propertyValue(s: String) : String? {
        if (propertyMap.isEmpty()) {
            properties?.properties?.forEach {
                propertyMap.put(it.nodeName, it.textContent.trim())
            }
        }
        return propertyMap[s]
    }
}

fun main(argv: Array<String>) {
    Pom2().read("/Users/beust/t/pom.xml")
}

class Pom2 {
    fun read(s: String) {
        val ins = File(s).inputStream()
        val jaxbContext = JAXBContext.newInstance(PomProject::class.java)
        val pom = jaxbContext.createUnmarshaller().unmarshal(ins) as PomProject
        println("License: " + pom.licenses?.licenses!![0].name)
        println("Developer: " + pom.developers?.developers!![0].name)
        println("Scm: " + pom.scm?.connection)
        println("Properties: " + pom.propertyValue("kotlin.version"))
        println("Plugin repositories: " + pom.pluginRepositories?.pluginRepository!![0])
    }
}

class Properties {
    @XmlAnyElement @JvmField
    val properties = arrayListOf<Element>()
}

class Developers {
    @XmlElement(name = "developer") @JvmField
    val developers = arrayListOf<Developer>()
}

class Developer {
    var name: String? = null
    var id: String? = null
}

class Licenses {
    @XmlElement(name = "license") @JvmField
    val licenses = arrayListOf<License>()
}

class License {
    var name: String? = null
    var url: String? = null
    var distribution: String? = null
}

class PluginRepositories {
    @XmlElement(name = "pluginRepository") @JvmField
    val pluginRepository = arrayListOf<PluginRepository>()
}

class PluginRepository {
    var id: String? = null
    var name: String? = null
    var url: String? = null
}

class Dependencies {
    @XmlElement(name = "dependency") @JvmField
    val dependencies = arrayListOf<Dependency>()
}

class Dependency {
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null
    var scope: String? = null
    var packaging: String? = null
}

class Scm {
    var connection: String? = null
    var developerConnection: String? = null
    var url: String? = null
}

class Parent {
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null
}

