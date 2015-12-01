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
        File(AndroidFiles.mergedResourcesNoVariant(project)).deleteRecursively()
        mergeResources(project, context.variant)
        mergeAndroidManifest(project, context.variant)
        log(2, "All done merging")
    }

    /**
     * TODO: not implemented yet, just copying the manifest to where the merged manifest should be.
     */
    private fun mergeAndroidManifest(project: Project, variant: Variant) {
        val dest = AndroidFiles.mergedManifest(project, variant)
        log(2, "----- Merging manifest (not implemented, copying it to $dest)")
        KFiles.copy(Paths.get(project.directory, "src/main/AndroidManifest.xml"), Paths.get(dest))
    }

    interface IFileMerger {
        fun canMerge(fromFile: File, toFile: File) : Boolean = true
        fun doMerge(fromFile: File, toFile: File)
    }

    /**
     * Merge files found in values/, e.g. values/strings.xml.
     * All the files are enumerated for each one, look if a file by the same name is present in the merged
     * directory. If not, copy it. If there is, look for a merger for this file and run it.
     */
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
                            log(3, "      Unconflicted string: ${it.name}")
                            toXml.strings.add(it)
                        } else {
                            log(3, "      String ${it.name} already present, ignoring")
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
            }
        }


        private fun readValuesXml(ins: InputStream) : ValuesXml {
            val jaxbContext = JAXBContext.newInstance(ValuesXml::class.java)
            val result = jaxbContext.createUnmarshaller().unmarshal(ins)
                    as ValuesXml
            return result

        }
    }

    /**
     * The default file merger does nothing (leaves the file in the merged directory alone).
     */
    class DefaultFileMerger : IFileMerger {
        override fun canMerge(fromFile: File, toFile: File) : Boolean = true
        override fun doMerge(fromFile: File, toFile: File) {
            log(3, "      DefaultMerger for $fromFile into $toFile, not doing anything")
        }
    }

    /**
     * Default file merger last.
     */
    val fileMergers : List<IFileMerger> = arrayListOf(ValuesFileMerger(), DefaultFileMerger())

    /**
     * Spec: http://developer.android.com/sdk/installing/studio-build.html
     */
    private fun mergeResources(project: Project, variant: Variant) {
        val dest = AndroidFiles.Companion.mergedResources(project, variant)
        log(2, "----- Merging res/ directory to $dest")
        listOf(variant.buildType.name, variant.productFlavor.name, "main").forEach {
            log(3, "  Current variant: $it")

            val fromDir = File(project.directory, "src/$it/res")
            KFiles.findRecursively(fromDir).forEach {
                val fromFile = File(fromDir, it)
                val toFile = File(dest, it)
                if (! toFile.exists()) {
                    log(3, "    Merge status for $it: COPY")
                    toFile.parentFile.mkdirs()
                    Files.copy(Paths.get(fromFile.absolutePath), Paths.get(toFile.absolutePath))
                } else {
                    val fileMerger = fileMergers.first { it.canMerge(fromFile, toFile) }
                    log(3, "    Merge status for $it: MERGE using ${fileMerger.javaClass}")
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
    var string: String? = null

    @XmlAttribute(name = "name") @JvmField
    var name: String? = null
}

fun String.forward() : String = replace("\\", "/")

