package com.beust.kobalt.maven

import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.ToString
import com.google.inject.assistedinject.Assisted
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.FileReader
import javax.xml.xpath.XPathConstants
import kotlin.dom.childElements

public class Pom @javax.inject.Inject constructor(@Assisted val id: String,
        @Assisted documentFile: java.io.File) : KobaltLogger {
    val XPATH_FACTORY = javax.xml.xpath.XPathFactory.newInstance()
    val XPATH = XPATH_FACTORY.newXPath()
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null
    var name: String? = null
    var properties = sortedMapOf<String, String>()

    public interface IFactory {
        fun create(@Assisted id: String, @Assisted documentFile : java.io.File) : Pom
    }

    data public class Dependency(val groupId: String, val artifactId: String, val version: String,
            val optional: Boolean = false, val scope: String? = null) : KobaltLogger {

        /** When a variable is used in a maven file, e.g. ${version} */
        private val VAR = "$" + "{"

        val mustDownload: Boolean
            get() = ! optional && "provided" != scope && "test" != scope

        val isValid : Boolean
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


        val id: String = "${groupId}:${artifactId}:${version}"
    }

    var dependencies = arrayListOf<Dependency>()

    init {
        val DEPENDENCIES = XPATH.compile("/project/dependencies/dependency")

        val document = kotlin.dom.parseXml(InputSource(FileReader(documentFile)))
        groupId = XPATH.compile("/project/groupId").evaluate(document)
        artifactId = XPATH.compile("/project/artifactId").evaluate(document)
        version = XPATH.compile("/project/version").evaluate(document)
        name = XPATH.compile("/project/name").evaluate(document)

        var list = XPATH.compile("/project/properties").evaluate(document, XPathConstants.NODESET) as NodeList
        var elem = list.item(0) as Element?
        elem.childElements().forEach {
            properties.put(it.nodeName, it.textContent)
        }

        val deps = DEPENDENCIES.evaluate(document, XPathConstants.NODESET) as NodeList
        for (i in 0..deps.length - 1) {
            val d = deps.item(i) as NodeList
            var groupId: String? = null
            var artifactId: String? = null
            var version: String = ""
            var optional: Boolean? = false
            var scope: String? = null
            for (j in 0..d.length - 1) {
                val e = d.item(j)
                if (e is Element) {
                    when (e.tagName) {
                        "groupId" -> groupId = e.textContent
                        "artifactId" -> artifactId = e.textContent
                        "version" -> version = e.textContent
                        "optional" -> optional = "true".equals(e.textContent, true)
                        "scope" -> scope = e.textContent
                    }
                }
            }
            log(3, "Done parsing: ${groupId} ${artifactId} ${version}")
            val tmpDependency = Dependency(groupId!!, artifactId!!, version, optional!!, scope)
            dependencies.add(tmpDependency)
        }
    }

    override public fun toString() = ToString("Pom", id, "id").s
}
