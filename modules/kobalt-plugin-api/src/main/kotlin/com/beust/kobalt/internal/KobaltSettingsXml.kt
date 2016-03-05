package com.beust.kobalt.internal

import com.beust.kobalt.homeDir
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File
import java.io.FileInputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/**
 * The root element of kobalt-settings.xml
 */
@XmlRootElement(name = "kobalt-settings")
class KobaltSettingsXml {
    @XmlElement @JvmField
    var localRepo: String = homeDir(KFiles.KOBALT_DOT_DIR, "repository")

    @XmlElement(name = "default-repos") @JvmField
    var defaultRepos: DefaultReposXml? = null
}

class DefaultReposXml {
    @XmlElement @JvmField
    var repo: List<String> = arrayListOf()
}

/**
 * The object Kobalt refers to for settings.
 */
@Singleton
class KobaltSettings @Inject constructor(val xmlFile: KobaltSettingsXml) {
    /**
     * Location of the local repo.
     */
    var localRepo = xmlFile.localRepo // var for testing

    val defaultRepos = xmlFile.defaultRepos?.repo

    companion object {
        val SETTINGS_FILE_PATH = homeDir(KFiles.KOBALT_DIR, "settings.xml")

        fun readSettingsXml() : KobaltSettings {
            val file = File(KobaltSettings.SETTINGS_FILE_PATH)
            if (file.exists()) {
                FileInputStream(file).use {
                    val jaxbContext = JAXBContext.newInstance(KobaltSettingsXml::class.java)
                    val xmlFile: KobaltSettingsXml = jaxbContext.createUnmarshaller().unmarshal(it)
                            as KobaltSettingsXml
                    val result = KobaltSettings(xmlFile)
                    return result
                }
            } else {
                log(2, "Couldn't find ${KobaltSettings.SETTINGS_FILE_PATH}, using default settings")
                return KobaltSettings(KobaltSettingsXml())
            }
        }
    }

}
