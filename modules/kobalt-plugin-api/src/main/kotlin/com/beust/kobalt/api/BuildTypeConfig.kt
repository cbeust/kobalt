package com.beust.kobalt.api

import com.beust.kobalt.api.annotation.Directive

class BuildTypeConfig(val project: Project?, val name: String) : IBuildConfig,
        IDependencyHolder by DependencyHolder(project) {
    var minifyEnabled = false
    var applicationIdSuffix: String? = null
    var proguardFile: String? = null

    override var buildConfig : BuildConfig? = BuildConfig()
}

@Directive
fun Project.buildType(name: String, init: BuildTypeConfig.() -> Unit) = BuildTypeConfig(this, name).apply {
    init()
    addBuildType(name, this)
}

