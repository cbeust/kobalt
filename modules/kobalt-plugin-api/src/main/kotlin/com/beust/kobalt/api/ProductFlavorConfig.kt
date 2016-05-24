package com.beust.kobalt.api

class ProductFlavorConfig(val name: String) : IBuildConfig,
        IDependencyHolder by DependencyHolder() {
    var applicationId: String? = null
    override var buildConfig : BuildConfig? = BuildConfig()
}


