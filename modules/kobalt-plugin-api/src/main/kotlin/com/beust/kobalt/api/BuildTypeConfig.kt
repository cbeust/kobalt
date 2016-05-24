package com.beust.kobalt.api

class BuildTypeConfig(val name: String) : IBuildConfig, IDependencyHolder by DependencyHolder() {

    var minifyEnabled = false
    var applicationIdSuffix: String? = null
    var proguardFile: String? = null

    override var buildConfig : BuildConfig? = BuildConfig()
}

