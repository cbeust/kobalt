package com.beust.kobalt.maven

import com.beust.kobalt.misc.*
import com.google.inject.assistedinject.*
import org.w3c.dom.*
import org.xml.sax.*
import java.io.*
import javax.xml.xpath.*
import kotlin.dom.*

public class Pom @javax.inject.Inject constructor(@Assisted val id: String,
        @Assisted documentFile: java.io.File) {
    val XPATH_FACTORY = javax.xml.xpath.XPathFactory.newInstance()
    val XPATH = XPATH_FACTORY.newXPath()
    var groupId: String? = null
    var artifactId: String? = null
    var packaging: String? = null
    var version: String? = null
    var name: String? = null
    var properties = sortedMapOf<String, String>()
    var repositories = listOf<String>()

    public interface IFactory {
        fun create(@Assisted id: String, @Assisted documentFile: java.io.File): Pom
    }

    data public class Dependency(val groupId: String, val artifactId: String, val packaging: String?,
        val version: String, val optional: Boolean = false, val scope: String? = null, val classifier: String? = null) {

        /** When a variable is used in a maven file, e.g. ${version} */
        private val VAR = "$" + "{"

        val mustDownload: Boolean
            get() = !optional && "provided" != scope && "test" != scope

        val isValid: Boolean
            get() {
                var result = false
                if (version.contains(VAR)) {
                    log(3, "Skipping variable version ${this}")
                } else if (groupId.contains(VAR)) {
                    log(3, "Skipping variable groupId ${this}")
                } else if (artifactId.contains(VAR)) {
                    log(3, "Skipping variable artifactId ${this}")
                } else {
                    result = true
                }
                return result
            }

        val id: String
                get() = listOf(groupId, artifactId, packaging, version, classifier).filterNotNull().joinToString(":")
    }

    var dependencies = arrayListOf<Dependency>()

    init {
        val DEPENDENCIES = XPATH.compile("/project/dependencies/dependency")

        val document = kotlin.dom.parseXml(InputSource(FileReader(documentFile)))
        groupId = XPATH.compile("/project/groupId").evaluate(document)
        artifactId = XPATH.compile("/project/artifactId").evaluate(document)
        version = XPATH.compile("/project/version").evaluate(document)
        name = XPATH.compile("/project/name").evaluate(document)
        var repositoriesList = XPATH.compile("/project/repositories").evaluate(document, XPathConstants.NODESET)
                as NodeList
        var repoElem = repositoriesList.item(0) as Element?
        repositories = repoElem.childElements().map({ it.getElementsByTagName("url").item(0).textContent })

        val propertiesList = XPATH.compile("/project/properties").evaluate(document, XPathConstants.NODESET) as NodeList
        var propsElem = propertiesList.item(0) as Element?
        propsElem.childElements().forEach {
            properties.put(it.nodeName, it.textContent)
        }

        val deps = DEPENDENCIES.evaluate(document, XPathConstants.NODESET) as NodeList
        for (i in 0..deps.length - 1) {
            val d = deps.item(i) as NodeList
            var groupId: String? = null
            var artifactId: String? = null
            var packaging: String? = null
            var version: String = ""
            var optional: Boolean? = false
            var scope: String? = null
            var classifier: String? = null

            for (j in 0..d.length - 1) {
                val e = d.item(j)
                if (e is Element) {
                    when (e.tagName) {
                        "groupId" -> groupId = e.textContent
                        "artifactId" -> artifactId = e.textContent
                        "type" -> packaging = e.textContent
                        "version" -> version = e.textContent
                        "optional" -> optional = "true".equals(e.textContent, true)
                        "scope" -> scope = e.textContent
                        "classifier" -> classifier = e.textContent
                    }
                }
            }
            val tmpDependency = Dependency(groupId!!, artifactId!!, packaging, version, optional!!, scope, classifier)
            log(3, "Done parsing: ${tmpDependency.id}")
            dependencies.add(tmpDependency)
        }
    }

    override public fun toString() = toString("Pom", id, "id")
}
