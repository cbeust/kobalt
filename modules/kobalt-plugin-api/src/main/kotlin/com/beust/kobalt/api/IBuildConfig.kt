package com.beust.kobalt.api

interface IBuildConfig {
    var buildConfig: BuildConfig?

    fun buildConfig(init: BuildConfig.() -> Unit) {
        buildConfig = BuildConfig().apply {
            init()
        }
    }
}