package com.beust.kobalt.plugin.android

import com.beust.kobalt.Variant
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement
import javax.xml.bind.annotation.XmlValue

/**
 * Merges manifests and resources.
 */
class Merger @Inject constructor() {
    fun merge(project: Project, context: KobaltContext) {
        mergeResources(project, context.variant)
        mergeAndroidManifest(project, context.variant)
        log(1, "Done merging")
    }

    /**
     * TODO: not implemented yet, just copying the manifest to where the merged manifest should be.
     */
    private fun mergeAndroidManifest(project: Project, variant: Variant) {
        val dest = AndroidFiles.mergedManifest(project, variant)
        log(1, "Manifest merging not implemented, copying it to $dest")
        KFiles.copy(Paths.get("app/src/main/AndroidManifest.xml"), Paths.get(dest))
    }

    interface IFileMerger {
        fun canMerge(fromFile: File, toFile: File) : Boolean = true
        fun doMerge(fromFile: File, toFile: File)
    }

    class ValuesFileMerger : IFileMerger {
        override fun canMerge(fromFile: File, toFile: File) : Boolean {
            return fromFile.parentFile.name == "values"
        }

        override fun doMerge(fromFile: File, toFile: File) {
            FileInputStream(toFile).use { toInputStream ->
                val toXml = readValuesXml(toInputStream)
                FileInputStream(fromFile).use { fromInputStream ->
                    val fromXml = readValuesXml(fromInputStream)
                    val seen = toXml.strings.map { it.name!! }.toHashSet<String>()
                    fromXml.strings.forEach {
                        if (!seen.contains(it.name!!)) {
                            log(1, "      Unconflicted string: ${it.name}")
                            toXml.strings.add(it)
                        } else {
                            log(1, "      String ${it.name} overwritten")
                        }
                    }
                }
                val mergedText = StringWriter()
                val pw = PrintWriter(mergedText)

                JAXBContext.newInstance(ValuesXml::class.java).createMarshaller().let { marshaller ->
                    with(marshaller) {
                        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
                        setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
                        marshal(toXml, pw)
                    }
                }
                KFiles.saveFile(toFile, mergedText.toString())
                log(1, "Wrote merged File: $toFile:\n" + mergedText.toString())
            }
        }


        private fun readValuesXml(ins: InputStream) : ValuesXml {
            val jaxbContext = JAXBContext.newInstance(ValuesXml::class.java)
            val result = jaxbContext.createUnmarshaller().unmarshal(ins)
                    as ValuesXml
            return result

        }
    }

    class DefaultFileMerger : IFileMerger {
        override fun canMerge(fromFile: File, toFile: File) : Boolean = true
        override fun doMerge(fromFile: File, toFile: File) {
            log(1, "    DefaultMerger for $fromFile into $toFile, not doing anything")
        }
    }

    val fileMergers : List<IFileMerger> = arrayListOf(ValuesFileMerger(), DefaultFileMerger())

    /**
     * TODO: not implemented yet, just copying the resources into the variant dir
     * Spec: http://developer.android.com/sdk/installing/studio-build.html
     */
    private fun mergeResources(project: Project, variant: Variant) {
        val dest = AndroidFiles.Companion.mergedResources(project, variant)
        log(1, "Resource merging not implemented, copying app/src/main/res to $dest")
        listOf(variant.buildType.name, variant.productFlavor.name, "main").forEach {
            log(1, "  CURRENT VARIANT: $it, Copying app/src/$it/res into $dest")

            val fromDir = File("app/src/$it/res")
            KFiles.findRecursively(fromDir).forEach {
                val fromFile = File(fromDir, it)
                val toFile = File(dest, it)
                if (! toFile.exists()) {
                    log(1, "    Copy $it to $toFile")
                    toFile.parentFile.mkdirs()
                    Files.copy(Paths.get(fromFile.absolutePath), Paths.get(toFile.absolutePath))
                } else {
                    val fileMerger = fileMergers.first { it.canMerge(fromFile, toFile) }
                    fileMerger.doMerge(fromFile, toFile)
                }

            }
        }
    }
}

/**
 * Represents a values/strings.xml file.
 */
@XmlRootElement(name = "resources")
class ValuesXml {
    @XmlElement(name = "string") @JvmField
    var strings: ArrayList<ValueString> = arrayListOf()
}

class ValueString {
    @XmlValue @JvmField
    val string: String? = null

    @XmlAttribute(name = "name") @JvmField
    val name: String? = null
}

fun String.forward() : String = replace("\\", "/")

