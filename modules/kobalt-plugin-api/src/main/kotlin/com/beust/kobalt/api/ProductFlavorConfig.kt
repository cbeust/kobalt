package com.beust.kobalt.api

import com.beust.kobalt.api.annotation.Directive

class ProductFlavorConfig(val project: Project?, val name: String) : IBuildConfig,
        IDependencyHolder by DependencyHolder(project) {
    var applicationId: String? = null
    override var buildConfig : BuildConfig? = BuildConfig()
}

@Directive
fun Project.productFlavor(name: String, init: ProductFlavorConfig.() -> Unit) = ProductFlavorConfig(this, name).apply {
    init()
    addProductFlavor(name, this)
}


