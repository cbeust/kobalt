package com.beust.kobalt.maven

import org.w3c.dom.Element
import java.io.File
import java.util.*
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
    val dependencies = arrayListOf<Dependency>()
    val pluginRepositories = arrayListOf<PluginRepository>()

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
    }
}

class Properties {
    @XmlAnyElement @JvmField
    var properties: ArrayList<Element>? = null
}

class Developers {
    @XmlElement(name = "developer") @JvmField
    var developers: ArrayList<Developer>? = null
}

class Developer {
    var name: String? = null
    var id: String? = null
}

class Licenses {
    @XmlElement(name = "license") @JvmField
    var licenses : ArrayList<License>? = null
}

class License {
    var name: String? = null
    var url: String? = null
    var distribution: String? = null
}

class PluginRepository {
    var id: String? = null
    var name: String? = null
    var url: String? = null
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

