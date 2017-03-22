package com.beust.kobalt.maven

import com.beust.kobalt.internal.KobaltSettings
import com.google.inject.Inject
import java.io.File
import javax.inject.Singleton

@Singleton
open class LocalRepo @Inject constructor(val kobaltSettings: KobaltSettings) {
    val localRepo: File
        get() = kobaltSettings.localCache

    fun toFullPath(path: String): String = File(localRepo, path).absolutePath
}


