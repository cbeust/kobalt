package com.beust.kobalt.plugin.android

import java.io.InputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/**
 * Parse AndroidManifest.xml and expose its content.
 */
class AndroidManifest(val ins: InputStream) {
    val manifest: AndroidManifestXml by lazy {
        val jaxbContext = JAXBContext.newInstance(AndroidManifestXml::class.java)
        jaxbContext.createUnmarshaller().unmarshal(ins) as AndroidManifestXml
    }

    val pkg by lazy {
        manifest.pkg
    }

    val mainActivity: String? by lazy {
        fun isLaunch(act: ActivityXml) : Boolean {
            val r = act.intentFilters.filter { inf: IntentFilter ->
                inf.action?.name == "android.intent.action.MAIN" &&
                        inf.category?.name == "android.intent.category.LAUNCHER"
            }
            return r.size > 0
        }
        val act = manifest.application?.activities?.filter { isLaunch(it) }
        if (act != null && act.size > 0) {
            act.get(0).name?.let { n ->
                if (n.startsWith(".")) pkg + "." + n.substring(1) else n
            }
        } else {
            null
        }
    }
}

@XmlRootElement(name = "manifest")
class AndroidManifestXml {
    @XmlAttribute(name = "package") @JvmField
    val pkg: String? = null
    var application: ApplicationXml? = null
}

class ApplicationXml {
    @XmlElement(name = "activity") @JvmField
    var activities: List<ActivityXml> = arrayListOf()
}

class ActivityXml {
    @XmlAttribute(namespace = "http://schemas.android.com/apk/res/android", name = "name") @JvmField
    var name: String? = null

    @XmlElement(name = "intent-filter") @JvmField
    var intentFilters: List<IntentFilter> = arrayListOf()
}

class IntentFilter {
    var action: ActionXml? = null
    var category: CategoryXml? = null
}

class ActionXml {
    @XmlAttribute(namespace = "http://schemas.android.com/apk/res/android", name = "name") @JvmField
    var name: String? = null
}

class CategoryXml {
    @XmlAttribute(namespace = "http://schemas.android.com/apk/res/android", name = "name") @JvmField
    var name: String? = null
}
