package com.beust.kobalt.plugin.android

import com.android.io.FileWrapper
import com.android.xml.AndroidManifest
import java.io.File

/**
 * Manage the main application id for the app, based on an overlay of the AndroidManifest.xml and
 * values specified in the Android config (in the build file).
 */
class AppInfo(val androidManifest: File, val config: AndroidConfig) {
    val abstractManifest = FileWrapper(androidManifest)

    private fun <T> overlay(manifestValue: T, configValue: T?) = configValue ?: manifestValue

    val versionCode : Int
        get() = overlay(AndroidManifest.getVersionCode(abstractManifest), config.versionCode)

    val versionName : String
        get() = versionCode.toString()

    val minSdkVersion: String?
        get() = overlay(AndroidManifest.getMinSdkVersion(abstractManifest), config.minSdkVersion)?.toString()

    val targetSdkVersion: String?
        get()  = overlay(AndroidManifest.getTargetSdkVersion(abstractManifest), config.targetSdkVersion)?.toString()
}
