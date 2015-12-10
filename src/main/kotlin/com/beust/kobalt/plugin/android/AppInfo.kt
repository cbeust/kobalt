package com.beust.kobalt.plugin.android

import com.android.io.FileWrapper
import com.android.xml.AndroidManifest
import java.io.File

/**
 * Manage the main application id for the app: values from androidConfig{} have precedence over values
 * found in the manifest.
 */
class AppInfo(val androidManifest: File, val config: AndroidConfig) {
    val abstractManifest = FileWrapper(androidManifest)

    val versionCode : Int
        get() = config.defaultConfig.versionCode ?: AndroidManifest.getVersionCode(abstractManifest)

    val versionName : String
        get() = config.defaultConfig.versionName ?: versionCode.toString()

    val minSdkVersion: String?
        get() = config.defaultConfig.minSdkVersion ?: AndroidManifest.getMinSdkVersion(abstractManifest)?.toString()

    val targetSdkVersion: String?
        get() = config.defaultConfig.targetSdkVersion
                ?: AndroidManifest.getTargetSdkVersion(abstractManifest)?.toString()
}
