package com.beust.kobalt.internal

import com.beust.kobalt.ProxyConfig
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
    @XmlElement(name = "local-repo") @JvmField
    var localRepo: String = homeDir(KFiles.KOBALT_DOT_DIR, "repository")

    @XmlElement(name = "default-repos") @JvmField
    var defaultRepos: DefaultReposXml? = null

    @XmlElement(name = "proxies") @JvmField
    var proxies: ProxiesXml? = null

    @XmlElement(name = "kobalt-compiler-version") @JvmField
    var kobaltCompilerVersion: String = "1.0.3"

    @XmlElement(name = "kobalt-compiler-repo") @JvmField
    var kobaltCompilerRepo: String? = null
}

class ProxiesXml {
    @XmlElement @JvmField
    var proxy: List<ProxyXml> = arrayListOf()
}

class ProxyXml {
    @XmlElement @JvmField
    var host: String = ""

    @XmlElement @JvmField
    var port: String = ""

    @XmlElement @JvmField
    var type: String = ""

    @XmlElement @JvmField
    var nonProxyHosts: String = ""
}

class DefaultReposXml {
    @XmlElement @JvmField
    var repo: List<String> = arrayListOf()
}

fun List<ProxyConfig>.getProxy(protocol:String) = find { it.type==protocol }

/**
 * The object Kobalt refers to for settings.
 */
@Singleton
class KobaltSettings @Inject constructor(val xmlFile: KobaltSettingsXml) {
    /**
     * Location of the local repo.
     */
    var localRepo = KFiles.makeDir(xmlFile.localRepo) // var for testing

    val defaultRepos = xmlFile.defaultRepos?.repo

    val proxyConfigs = with(xmlFile.proxies?.proxy) {
        fun toIntOr(s: String, defaultValue: Int) = try {   //TODO can be extracted to some global Utils
            s.toInt()
        } catch(e: NumberFormatException) {
            defaultValue
        }

        if (this != null) {
            map {proxyXml->
                ProxyConfig(proxyXml.host, toIntOr(proxyXml.port, 0), proxyXml.type, proxyXml.nonProxyHosts)
            }
        } else null
    }

    var kobaltCompilerVersion = xmlFile.kobaltCompilerVersion
    var kobaltCompilerRepo = xmlFile.kobaltCompilerRepo

    companion object {
        val SETTINGS_FILE_PATH = KFiles.joinDir(KFiles.HOME_KOBALT_DIR.absolutePath, "settings.xml")

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
