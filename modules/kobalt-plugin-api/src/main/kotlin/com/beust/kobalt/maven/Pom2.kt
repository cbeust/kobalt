package com.beust.kobalt.maven

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.FileReader
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAnyElement
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.sax.SAXSource

@XmlRootElement(name = "project")
class PomProject {
    var modelVersion: String? = null
    var groupId: String? = null
        get() {
            if (field != null && field!!.contains("\${")) {
                println("VARIABLE GROUP")
            }
            return field
        }
    var artifactId: String? = null
    var version: String? = null
        get() {
            if (field != null && field!!.contains("\${")) {
                println("VARIABLE VERSION")
            }
            return field
        }
    var name: String? = null
    var description: String? = null
    var url: String? = null
    var packaging: String? = null
    var licenses : Licenses? = null
    var developers: Developers? = null
    var scm: Scm? = null
    var properties: Properties? = null
    var parent: Parent? = null
    @XmlElement(name = "dependencies") @JvmField
    var pomDependencies: Dependencies? = null
    val dependencies: List<Dependency>
        get() =
            if (pomDependencies != null) pomDependencies!!.dependencies
            else emptyList<Dependency>()

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
    val p = Pom2(File("/Users/beust/t/pom.xml"))
    val pom = p.pom
    println("Dependencies: " + pom.dependencies[0])
}

class Pom2(val documentFile: File) {
    val pom: PomProject by lazy {
        val ins = documentFile.inputStream()
        val jaxbContext = JAXBContext.newInstance(PomProject::class.java)
        val unmarshaller = jaxbContext.createUnmarshaller()

        val sax = SAXParserFactory.newInstance()
        sax.isNamespaceAware = false
        val reader = sax.newSAXParser().xmlReader
        val er = SAXSource(reader, InputSource(FileReader(documentFile)))

        unmarshaller.unmarshal(er) as PomProject
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
    var groupId: String = ""
    fun groupId(pom: Pom2) : String = expandVariable(groupId, pom)

    var artifactId: String = ""
    fun artifactId(pom: Pom2) : String = expandVariable(artifactId, pom)

    var version: String = ""
    fun version(pom: Pom2) : String = expandVariable(version, pom)

    var optional: String = "false"
    var scope: String = ""
    var packaging: String = ""

    val id: String = "$groupId:$artifactId:$version"

    val mustDownload: Boolean
        get() = ! optional.toBoolean() && "provided" != scope && "test" != scope

    val isValid : Boolean get() = ! isVariable(groupId) && ! isVariable(artifactId) && ! isVariable(version)

    private fun isVariable(s: String) = s.startsWith("\${") && s.endsWith("}")

    private fun expandVariable(s: String, pom: Pom2) : String {
        if (isVariable(s)) {
            println("Expanding variable $s")
            return s
        } else {
            return s
        }
    }
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

