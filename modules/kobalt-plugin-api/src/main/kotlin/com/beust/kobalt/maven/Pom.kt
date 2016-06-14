package com.beust.kobalt.maven

import com.beust.kobalt.misc.toString
import com.beust.kobalt.misc.warn
import com.google.inject.assistedinject.Assisted
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants

class Pom @javax.inject.Inject constructor(@Assisted val id: String,
        @Assisted documentFile: java.io.File) {
    val XPATH_FACTORY = javax.xml.xpath.XPathFactory.newInstance()
    val XPATH = XPATH_FACTORY.newXPath()
    var groupId: String? = null
    var artifactId: String? = null
    var packaging: String? = null
    var version: String? = null

    /**
     * If the version is a string, extract it, look it up and evaluate it if we find it. Otherwise, error.
     */
    private fun calculateVersion(s: String) : String {
        val v = extractVar(s)
        if (v != null) {
            val value = properties[v]
            if (value != null) {
                return value
            } else {
                warn("Unknown variable for version: " + s)
                return ""
            }
        } else {
            return s
        }
    }

    private fun extractVar(s: String) : String? {
        if (s.startsWith("\${") && s.endsWith("}")) {
            return s.substring(2, s.length - 1)
        } else {
            return null
        }
    }

    var name: String? = null
    var properties = sortedMapOf<String, String>()
    var repositories = listOf<String>()

    interface IFactory {
        fun create(@Assisted id: String, @Assisted documentFile: java.io.File): Pom
    }

    data class Dependency(val groupId: String, val artifactId: String, val packaging: String?,
        val version: String, val optional: Boolean = false, val scope: String? = null) {

        val mustDownload: Boolean
            get() = !optional && "provided" != scope && "test" != scope

        val id: String = "$groupId:$artifactId:$version"
    }

    val dependencies = arrayListOf<Dependency>()

    init {
        val DEPENDENCIES = XPATH.compile("/project/dependencies/dependency")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(documentFile)

        groupId = XPATH.compile("/project/groupId").evaluate(document)
        artifactId = XPATH.compile("/project/artifactId").evaluate(document)
        version = XPATH.compile("/project/version").evaluate(document)
        name = XPATH.compile("/project/name").evaluate(document)
        var repositoriesList = XPATH.compile("/project/repositories").evaluate(document, XPathConstants.NODESET)
                as NodeList
        var repoElem = repositoriesList.item(0) as Element?
        repositories = childElements(repoElem).map({ it.getElementsByTagName("url").item(0)
                .textContent })

        val propertiesList = XPATH.compile("/project/properties").evaluate(document, XPathConstants.NODESET) as NodeList
        var propsElem = propertiesList.item(0) as Element?
        childElements(propsElem).forEach {
            properties.put(it.nodeName, it.textContent)
        }

        val deps = DEPENDENCIES.evaluate(document, XPathConstants.NODESET) as NodeList
        for (i in 0..deps.length - 1) {
            val d = deps.item(i) as NodeList
            var groupId: String? = null
            var artifactId: String? = null
            var packaging: String? = null
            var readVersion: String = ""
            var optional: Boolean? = false
            var scope: String? = null
            for (j in 0..d.length - 1) {
                val e = d.item(j)
                if (e is Element) {
                    when (e.tagName) {
                        "groupId" -> groupId = e.textContent
                        "artifactId" -> artifactId = e.textContent
                        "type" -> packaging = e.textContent
                        "version" -> readVersion = e.textContent
                        "optional" -> optional = "true".equals(e.textContent, true)
                        "scope" -> scope = e.textContent
                    }
                }
            }
            val version = calculateVersion(readVersion)
            val tmpDependency = Dependency(groupId!!, artifactId!!, packaging, version, optional!!, scope)
            dependencies.add(tmpDependency)
        }
    }

    private fun childElements(repoElem: Element?): List<Element> {
        val result = arrayListOf<Element>()
        if (repoElem != null) {
            for (i in 0..repoElem.childNodes.length - 1) {
                val elem = repoElem.childNodes.item(i)
                if (elem is Element) {
                    result.add(elem)
                }
            }
        }
        return result
    }

    override fun toString() = toString("Pom", "id", id)
}
