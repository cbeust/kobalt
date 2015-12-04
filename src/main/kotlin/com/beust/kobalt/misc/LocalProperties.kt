package com.beust.kobalt.misc

import com.beust.kobalt.KobaltException
import com.google.inject.Singleton
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@Singleton
class LocalProperties {
    var docUrl: String? = null
    val localProperties: Properties by lazy {
        val result = Properties()
        val filePath = Paths.get("local.properties")
        if (! Files.exists(filePath)) {
            warn("Couldn't find a local.properties file")
        }

        filePath.let { path ->
            if (Files.exists(path)) {
                Files.newInputStream(path).use {
                    result.load(it)
                }
            }
        }

        result
    }

    fun get(name: String, docUrl: String? = null) : String {
        this.docUrl = docUrl
        val result = localProperties.getProperty(name)
                ?: throw KobaltException("Couldn't find $name in local.properties", docUrl = docUrl)
        return result as String
    }
}
