package com.beust.kobalt.internal

class DocUrl {
    companion object {
        private const val HOST = "https://beust.com/kobalt/"
        private fun url(path: String) = HOST + path

        val PUBLISH_PLUGIN_URL = url("plug-ins/index.html#publishing")
    }
}

