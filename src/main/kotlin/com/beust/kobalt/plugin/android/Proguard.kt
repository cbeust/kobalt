package com.beust.kobalt.plugin.android

import com.beust.kobalt.misc.KFiles

class Proguard(val androidHome: String) {
    val proguardHome = KFiles.joinDir(androidHome, "tools", "proguard")
    val proguardCommand = KFiles.joinDir(proguardHome, "bin", "proguard.sh")

    fun getDefaultProguardFile(name: String) = KFiles.joinDir(proguardHome, name)
}
