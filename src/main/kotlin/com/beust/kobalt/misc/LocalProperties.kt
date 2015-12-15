package com.beust.kobalt.misc

import com.beust.kobalt.KobaltException
import com.google.inject.Singleton
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@Singleton
class LocalProperties {
    val localProperties: Properties by lazy {
        val result = Properties()
        val filePath = Paths.get("local.properties")
        filePath.let { path ->
            if (path.toFile().exists()) {
                Files.newInputStream(path).use {
                    result.load(it)
                }
            }
        }

        result
    }

    fun getNoThrows(name: String, docUrl: String? = null) = localProperties.getProperty(name)

    fun get(name: String, docUrl: String? = null) : String {
        val result = getNoThrows(name, docUrl)
                ?: throw KobaltException("Couldn't find $name in local.properties", docUrl = docUrl)
        return result as String
    }
}
